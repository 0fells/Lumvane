#include "include/database/GroupsRepo.h"
#include "include/database/ProfilesRepo.h"
#include "include/database/RoutesRepo.h"
#include "include/database/TrafficStatsRepo.h"

#include "include/global/Configs.hpp"

#include <QDir>
#include <QFileInfo>
#include <QThread>

namespace Configs {
    std::string DatabaseManager::deriveStatsDbPath(const std::string& dbPath) {
        const QFileInfo fi(QString::fromStdString(dbPath));
        return QDir(fi.absolutePath()).filePath("lumvane_stats.db").toStdString();
    }

    DatabaseManager::DatabaseManager(const std::string& dbPath)
        : db(dbPath), statsDb(deriveStatsDbPath(dbPath), true) {
        createEntityIdsTable(db);
        
        initializeRepos();
    }

    void DatabaseManager::createEntityIdsTable(Database& db) {
        db.exec(R"(
            CREATE TABLE IF NOT EXISTS entity_ids (
                profile_last_id INTEGER NOT NULL DEFAULT 0,
                group_last_id INTEGER NOT NULL DEFAULT 0,
                route_profile_last_id INTEGER NOT NULL DEFAULT 0
            )
        )");
        
        auto checkQuery = db.query("SELECT COUNT(*) FROM entity_ids");
        int count = 0;
        if (checkQuery && checkQuery->executeStep()) {
            count = checkQuery->getColumn(0).getInt();
        }
        
        if (count == 0) {
            db.exec(R"(
                INSERT INTO entity_ids (profile_last_id, group_last_id, route_profile_last_id)
                VALUES (0, 0, 0)
            )");
        }
    }
    
    void DatabaseManager::RunDeferredMaintenance() {
        runOnNewThread([this] {
            QThread::msleep(MAINTENANCE_DELAY_MS);
            db.RunMaintenance();
            statsDb.RunMaintenance();
        });
    }

    void DatabaseManager::initializeRepos() {
        profilesRepo = std::make_unique<ProfilesRepo>(db);
        groupsRepo = std::make_unique<GroupsRepo>(db);
        routesRepo = std::make_unique<RoutesRepo>(db);
        settingsRepo = std::make_unique<SettingsRepo>(db);
        trafficStatsRepo = std::make_unique<TrafficStatsRepo>(statsDb);
    }
}
