#!/bin/bash

# SSL Certificate Auto-Renewal Setup Script
# This script sets up automatic certificate renewal using cron

set -e

SCRIPT_DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
RENEW_SCRIPT="$SCRIPT_DIR/renew-cert.sh"

echo "=== Setting up SSL Certificate Auto-Renewal ==="
echo "Project directory: $PROJECT_DIR"
echo "Renewal script: $RENEW_SCRIPT"

# Make renewal script executable
chmod +x "$RENEW_SCRIPT"
echo "✓ Made renewal script executable"

# Create cron job (runs every day at 3 AM)
CRON_JOB="0 3 * * * $RENEW_SCRIPT >> /var/log/certbot-renewal.log 2>&1"

# Check if cron job already exists
if crontab -l 2>/dev/null | grep -q "$RENEW_SCRIPT"; then
    echo "⚠ Cron job already exists"
else
    # Add cron job
    (crontab -l 2>/dev/null; echo "$CRON_JOB") | crontab -
    echo "✓ Added cron job to run renewal script daily at 3 AM"
fi

# Create log file
sudo touch /var/log/certbot-renewal.log
sudo chmod 644 /var/log/certbot-renewal.log
echo "✓ Created log file: /var/log/certbot-renewal.log"

echo ""
echo "=== Setup Complete ==="
echo ""
echo "Certificate renewal will run automatically every day at 3 AM."
echo "Certbot will only renew certificates that are within 30 days of expiration."
echo ""
echo "To check the cron job:"
echo "  crontab -l"
echo ""
echo "To view renewal logs:"
echo "  tail -f /var/log/certbot-renewal.log"
echo ""
echo "To manually test renewal (dry-run):"
echo "  sudo certbot renew --dry-run"
echo ""
echo "To manually renew now:"
echo "  $RENEW_SCRIPT"
echo ""
