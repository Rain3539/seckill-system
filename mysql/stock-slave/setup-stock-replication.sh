#!/bin/bash
# 等待 stock-mysql-master 就绪
until mysqladmin ping -h stock-mysql-master -u root -proot123 --silent 2>/dev/null; do
    echo "Waiting for stock-mysql-master..."
    sleep 3
done

# 获取主库状态
MASTER_STATUS=$(mysql -h stock-mysql-master -u root -proot123 -e "SHOW MASTER STATUS\G" 2>/dev/null)
LOG_FILE=$(echo "$MASTER_STATUS" | grep "File:" | awk '{print $2}')
LOG_POS=$(echo "$MASTER_STATUS" | grep "Position:" | awk '{print $2}')

echo "Master file: $LOG_FILE, position: $LOG_POS"

# 配置从库复制
mysql -u root -proot123 -e "
STOP SLAVE;
RESET SLAVE;
CHANGE MASTER TO
    MASTER_HOST='stock-mysql-master',
    MASTER_PORT=3306,
    MASTER_USER='stock_repl',
    MASTER_PASSWORD='StockRepl@123',
    MASTER_LOG_FILE='$LOG_FILE',
    MASTER_LOG_POS=$LOG_POS;
START SLAVE;
" 2>/dev/null

echo "Stock replication setup complete."
