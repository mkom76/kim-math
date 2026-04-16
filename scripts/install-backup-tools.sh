#!/bin/bash
# Install tools needed for MySQL backup to S3
# Run this once on the server after deployment

echo "Installing backup tools..."

# Install s3cmd (S3-compatible CLI tool)
sudo apt update
sudo apt install -y s3cmd python3-dateutil

echo "✓ s3cmd installed"

# Configure s3cmd (alternative: use .env variables in backup script)
echo ""
echo "s3cmd is installed. The backup script uses .env variables for configuration."
echo "No additional configuration needed."
echo ""
echo "To test S3 connection:"
echo "  source /home/suhui/kim-math/.env"
echo "  s3cmd ls --access_key=\$S3_ACCESS_KEY --secret_key=\$S3_SECRET_KEY --host=\${S3_ENDPOINT#https://} --region=\$S3_REGION"
