-- This file is executed when MySQL container is first initialized
-- It ensures the database is created with proper character set

-- Database is already created by MYSQL_DATABASE env var,
-- but we'll ensure it has the correct character set
ALTER DATABASE kim_math CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- Grant privileges (user is already created by MYSQL_USER env var)
-- This is just a safety measure
FLUSH PRIVILEGES;
