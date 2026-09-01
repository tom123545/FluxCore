CREATE DATABASE IF NOT EXISTS fluxcore_approval CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fluxcore_procurement CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fluxcore_contract CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS fluxcore_notification CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

GRANT ALL PRIVILEGES ON fluxcore_approval.* TO 'fluxcore'@'%';
GRANT ALL PRIVILEGES ON fluxcore_procurement.* TO 'fluxcore'@'%';
GRANT ALL PRIVILEGES ON fluxcore_contract.* TO 'fluxcore'@'%';
GRANT ALL PRIVILEGES ON fluxcore_notification.* TO 'fluxcore'@'%';
FLUSH PRIVILEGES;
