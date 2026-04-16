#!/bin/bash
set -e

# MySQL Backup to DigitalOcean Spaces (S3)
# This script dumps MySQL database and uploads to S3-compatible storage

# Load environment variables
if [ -f /home/suhui/kim-math/.env ]; then
    source /home/suhui/kim-math/.env
else
    echo "Error: .env file not found!"
    exit 1
fi

# Configuration
BACKUP_DIR="/home/suhui/backups"
DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_FILE="kim_math_backup_${DATE}.sql.gz"
CONTAINER_NAME="kim-math-mysql"

# Create backup directory if not exists
mkdir -p $BACKUP_DIR

echo "========================================="
echo "MySQL Backup Started: $(date)"
echo "========================================="

# 1. Create MySQL dump from Docker container
echo "Creating database dump..."
docker exec $CONTAINER_NAME mysqldump \
    -u root \
    -p${MYSQL_ROOT_PASSWORD} \
    --single-transaction \
    --routines \
    --triggers \
    --events \
    ${MYSQL_DATABASE} | gzip > $BACKUP_DIR/$BACKUP_FILE

# Check if dump was successful
if [ $? -eq 0 ]; then
    BACKUP_SIZE=$(du -h $BACKUP_DIR/$BACKUP_FILE | cut -f1)
    echo "✓ Database dump created: $BACKUP_FILE ($BACKUP_SIZE)"
else
    echo "✗ Database dump failed!"
    exit 1
fi

# 2. Upload to DigitalOcean Spaces (S3)
echo "Uploading to DigitalOcean Spaces..."

# Using s3cmd (S3-compatible tool)
s3cmd put $BACKUP_DIR/$BACKUP_FILE \
    s3://${S3_BUCKET}/mysql-backups/$BACKUP_FILE \
    --access_key=${S3_ACCESS_KEY} \
    --secret_key=${S3_SECRET_KEY} \
    --host=${S3_ENDPOINT#https://} \
    --host-bucket='%(bucket)s.sgp1.digitaloceanspaces.com' \
    --region=${S3_REGION}

if [ $? -eq 0 ]; then
    echo "✓ Backup uploaded to S3: s3://${S3_BUCKET}/mysql-backups/$BACKUP_FILE"
else
    echo "✗ S3 upload failed!"
    exit 1
fi

# 3. Keep only last 30 days of local backups
echo "Cleaning up old local backups (older than 30 days)..."
find $BACKUP_DIR -name "kim_math_backup_*.sql.gz" -mtime +30 -delete
echo "✓ Old local backups cleaned"

# 4. Keep only last 90 days on S3 (optional - saves storage costs)
echo "Cleaning up old S3 backups (older than 90 days)..."
CUTOFF_DATE=$(date -d "90 days ago" +%Y%m%d)

s3cmd ls s3://${S3_BUCKET}/mysql-backups/ \
    --access_key=${S3_ACCESS_KEY} \
    --secret_key=${S3_SECRET_KEY} \
    --host=${S3_ENDPOINT#https://} \
    --region=${S3_REGION} | while read -r line; do

    BACKUP_DATE=$(echo $line | grep -oP 'kim_math_backup_\K\d{8}')

    if [ ! -z "$BACKUP_DATE" ] && [ $BACKUP_DATE -lt $CUTOFF_DATE ]; then
        FILE_PATH=$(echo $line | awk '{print $4}')
        echo "Deleting old backup: $FILE_PATH"
        s3cmd del $FILE_PATH \
            --access_key=${S3_ACCESS_KEY} \
            --secret_key=${S3_SECRET_KEY} \
            --host=${S3_ENDPOINT#https://} \
            --region=${S3_REGION}
    fi
done

echo "✓ Old S3 backups cleaned"

echo "========================================="
echo "Backup Completed Successfully!"
echo "Local: $BACKUP_DIR/$BACKUP_FILE"
echo "S3: s3://${S3_BUCKET}/mysql-backups/$BACKUP_FILE"
echo "Completed: $(date)"
echo "========================================="

# Send notification (optional - requires mail setup)
# echo "MySQL backup completed successfully" | mail -s "Kim-Math Backup Success" admin@example.com
