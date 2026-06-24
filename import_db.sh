#!/bin/bash

# Color definitions for terminal output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[0;33m'
BLUE='\033[0;36m'
NC='\033[0m' # No Color

echo -e "${BLUE}=========================================${NC}"
echo -e "${BLUE}    TeamFlowAI Database One-Click Import  ${NC}"
echo -e "${BLUE}=========================================${NC}"

# Default connection settings from application.yml
DB_HOST="localhost"
DB_USER="root"
DB_PASS="root1234"
DB_NAME="teamflow_ai"
SQL_FILE="./database/teamflow_ai.sql"

# Check if SQL file exists
if [ ! -f "$SQL_FILE" ]; then
    echo -e "${RED}[ERROR] SQL file not found at $SQL_FILE${NC}"
    echo -e "${YELLOW}Please run this script from the project root directory: /Users/dexley/Documents/TeamFlowAI${NC}"
    exit 1
fi

# Detect mysql CLI tool
if ! command -v mysql &> /dev/null; then
    # Try common Homebrew macOS paths
    if [ -f "/opt/homebrew/bin/mysql" ]; then
        MYSQL_CMD="/opt/homebrew/bin/mysql"
    elif [ -f "/usr/local/bin/mysql" ]; then
        MYSQL_CMD="/usr/local/bin/mysql"
    else
        echo -e "${RED}[ERROR] 'mysql' command line tool was not found in your PATH.${NC}"
        echo -e "${YELLOW}Suggestions:${NC}"
        echo -e "  1. If you installed MySQL via Homebrew, try running: ${GREEN}export PATH=\"/opt/homebrew/opt/mysql-client/bin:\$PATH\"${NC}"
        echo -e "  2. Or specify the full path to your mysql command below."
        read -p "Enter mysql path (or press Enter to exit): " CUSTOM_MYSQL
        if [ -z "$CUSTOM_MYSQL" ] || [ ! -f "$CUSTOM_MYSQL" ]; then
            echo -e "${RED}Import cancelled.${NC}"
            exit 1
        fi
        MYSQL_CMD="$CUSTOM_MYSQL"
    fi
else
    MYSQL_CMD="mysql"
fi

echo -e "${YELLOW}Connecting to MySQL with:${NC}"
echo -e "  Host:     $DB_HOST"
echo -e "  User:     $DB_USER"
echo -e "  Password: [configured in script]"
echo -e "  Database: $DB_NAME"
echo ""

# Execute the SQL file
echo -e "${BLUE}[1/2] Importing SQL schema and data...${NC}"
$MYSQL_CMD -h "$DB_HOST" -u "$DB_USER" -p"$DB_PASS" < "$SQL_FILE" 2>/tmp/db_import_err.log

if [ $? -eq 0 ]; then
    echo -e "${GREEN}[SUCCESS] Database imported successfully!${NC}"
    echo -e "${GREEN}Database 'teamflow_ai' is now ready.${NC}"
    rm -f /tmp/db_import_err.log
else
    echo -e "${RED}[ERROR] Database import failed.${NC}"
    echo -e "${RED}Error log:${NC}"
    cat /tmp/db_import_err.log
    rm -f /tmp/db_import_err.log
    exit 1
fi

echo -e "${BLUE}=========================================${NC}"
