#!/bin/bash

# SSL Certificate Renewal Script
# This script renews Let's Encrypt certificates and reloads nginx

set -e

echo "=== SSL Certificate Renewal Started at $(date) ==="

# Renew certificates
echo "Renewing certificates..."
certbot renew --quiet

# Reload nginx in Docker container
echo "Reloading nginx..."
cd /home/suhui/kim-math
docker-compose -f docker-compose.prod.yml exec nginx nginx -s reload

echo "=== SSL Certificate Renewal Completed at $(date) ==="
echo ""
