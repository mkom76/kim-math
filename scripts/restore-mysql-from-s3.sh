#!/bin/bash
set -e

# MySQL Restore from DigitalOcean Spaces (S3)
# This script downloads a backup from S3 and restores to MySQL

# Load environment variables
if [ -f /home/suhui/kim-math/.env ]; then
    source /home/suhui/kim-math/.env
else
    echo "Error: .env file not found!"
    exit 1
fi

# Configuration
BACKUP_DIR="/home/suhui/backups"
CONTAINER_NAME="kim-math-mysql"

echo "========================================="
echo "MySQL Restore Tool"
echo "========================================="

# Check if backup file is specified
if [ -z "$1" ]; then
    echo "Usage: $0 <backup-filename>"
    echo ""
    echo "Available backups on S3:"
    s3cmd ls s3://${S3_BUCKET}/mysql-backups/ \
        --access_key=${S3_ACCESS_KEY} \
        --secret_key=${S3_SECRET_KEY} \
        --host=${S3_ENDPOINT#https://} \
        --region=${S3_REGION}
    exit 1
fi

BACKUP_FILE=$1

# Download from S3
echo "Downloading backup from S3..."
s3cmd get s3://${S3_BUCKET}/mysql-backups/$BACKUP_FILE \
    $BACKUP_DIR/$BACKUP_FILE \
    --access_key=${S3_ACCESS_KEY} \
    --secret_key=${S3_SECRET_KEY} \
    --host=${S3_ENDPOINT#https://} \
    --region=${S3_REGION} \
    --force

if [ $? -eq 0 ]; then
    echo "✓ Backup downloaded: $BACKUP_FILE"
else
    echo "✗ Download failed!"
    exit 1
fi

# Confirm restore
echo ""
echo "WARNING: This will restore the database to the backup state!"
echo "All current data will be replaced!"
read -p "Are you sure you want to continue? (yes/no): " CONFIRM

if [ "$CONFIRM" != "yes" ]; then
    echo "Restore cancelled."
    exit 0
fi

# Restore database
echo "Restoring database..."
gunzip < $BACKUP_DIR/$BACKUP_FILE | docker exec -i $CONTAINER_NAME mysql \
    -u root \
    -p${MYSQL_ROOT_PASSWORD} \
    ${MYSQL_DATABASE}

if [ $? -eq 0 ]; then
    echo "✓ Database restored successfully!"
    echo ""
    echo "Restart the backend container to ensure connection:"
    echo "  docker-compose -f docker-compose.prod.yml restart backend"
else
    echo "✗ Restore failed!"
    exit 1
fi

echo "========================================="
echo "Restore Completed!"
echo "========================================="
