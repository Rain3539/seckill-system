#!/bin/bash
# ──────────────────────────────────────────────────────────────────────
# setup-replication.sh
# 在 mysql-slave 容器内执行，自动完成主从复制配置
# docker-compose exec mysql-slave bash /docker-entrypoint-initdb.d/setup-replication.sh
# ──────────────────────────────────────────────────────────────────────
set -e

MASTER_HOST="${MYSQL_MASTER_HOST:-mysql-master}"
MASTER_PORT="${MYSQL_MASTER_PORT:-3306}"
MASTER_USER="replicator"
MASTER_PASS="Replicator@123"
ROOT_PASS="${MYSQL_ROOT_PASSWORD:-root123}"

echo ">>> 等待主库就绪..."
until mysqladmin ping -h"$MASTER_HOST" -P"$MASTER_PORT" -u root -p"$ROOT_PASS" --silent 2>/dev/null; do
    echo "    主库未就绪，等待 3s..."
    sleep 3
done
echo ">>> 主库已就绪"

echo ">>> 获取主库 binlog 位置..."
MASTER_STATUS=$(mysql -h"$MASTER_HOST" -P"$MASTER_PORT" -u root -p"$ROOT_PASS" \
    -e "SHOW MASTER STATUS\G" 2>/dev/null)

BINLOG_FILE=$(echo "$MASTER_STATUS" | grep "File:"     | awk '{print $2}')
BINLOG_POS=$(echo  "$MASTER_STATUS" | grep "Position:" | awk '{print $2}')

echo "    File=$BINLOG_FILE  Position=$BINLOG_POS"

echo ">>> 配置从库复制..."
mysql -u root -p"$ROOT_PASS" << SQL
STOP SLAVE;
RESET SLAVE ALL;
CHANGE MASTER TO
    MASTER_HOST='${MASTER_HOST}',
    MASTER_PORT=${MASTER_PORT},
    MASTER_USER='${MASTER_USER}',
    MASTER_PASSWORD='${MASTER_PASS}',
    MASTER_LOG_FILE='${BINLOG_FILE}',
    MASTER_LOG_POS=${BINLOG_POS};
START SLAVE;
SQL

echo ">>> 等待 Slave 线程启动..."
sleep 2

echo ">>> 复制状态："
mysql -u root -p"$ROOT_PASS" -e "SHOW SLAVE STATUS\G" 2>/dev/null \
    | grep -E "Slave_IO_Running|Slave_SQL_Running|Seconds_Behind_Master|Last_Error"

echo ">>> 主从复制配置完成 ✅"
