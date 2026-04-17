#!/bin/bash

# Setup Swap Memory for Low-RAM Servers
# This script creates a 2GB swap file to help with Docker builds

set -e

echo "=== Setting up 2GB Swap Memory ==="

# Check if swap already exists
if [ -f /swapfile ]; then
    echo "Swap file already exists. Removing old one..."
    sudo swapoff /swapfile
    sudo rm /swapfile
fi

echo "Creating 2GB swap file..."
sudo fallocate -l 2G /swapfile

echo "Setting permissions..."
sudo chmod 600 /swapfile

echo "Setting up swap space..."
sudo mkswap /swapfile

echo "Enabling swap..."
sudo swapon /swapfile

echo "Making swap permanent..."
if ! grep -q '/swapfile' /etc/fstab; then
    echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab
fi

echo ""
echo "=== Swap Setup Complete ==="
echo ""
echo "Memory status:"
free -h

echo ""
echo "Swap is now active and will persist after reboot."
echo "You can now run docker-compose builds without running out of memory."
