#pragma once

#include "Database.h"
#include "include/database/SettingsRepo.h"
#include <string>
#include <memory>

namespace Configs {
    class RoutesRepo;
    class GroupsRepo;
    class ProfilesRepo;
    class TrafficStatsRepo;

    void initDB(const std::string& dbPath);

    class DatabaseManager {
    private:
        Database db;
        Database statsDb;

        static void createEntityIdsTable(Database& db);
        static std::string deriveStatsDbPath(const std::string& dbPath);
        void initializeRepos();
    public:
        std::unique_ptr<ProfilesRepo> profilesRepo;
        std::unique_ptr<GroupsRepo> groupsRepo;
        std::unique_ptr<RoutesRepo> routesRepo;
        std::unique_ptr<SettingsRepo> settingsRepo;
        std::unique_ptr<TrafficStatsRepo> trafficStatsRepo;

        explicit DatabaseManager(const std::string& dbPath);
        ~DatabaseManager() = default;

        void RunDeferredMaintenance();
        
        DatabaseManager(const DatabaseManager&) = delete;
        DatabaseManager& operator=(const DatabaseManager&) = delete;
        
        Database& getDatabase() { return db; }
        const Database& getDatabase() const { return db; }
    };

    inline DatabaseManager* dataManager;
}
