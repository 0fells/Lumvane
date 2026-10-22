#pragma once

#include "Database.h"
#include "include/database/entities/RouteProfile.h"
#include <3rdparty/SQLiteCpp/include/SQLiteCpp.h>
#include <memory>
#include <mutex>
#include <map>
#include <QString>
#include <QJsonObject>
#include <QJsonDocument>
#include <QJsonArray>
#include <QMutex>

namespace Configs {
    class RoutesRepo {
    private:
        Database& db;
        mutable std::mutex mutex;
        mutable std::map<int, std::weak_ptr<RouteProfile>> identityMap;

        QJsonObject routeRuleToJson(const RouteRule* rule) const;
        
        std::shared_ptr<RouteRule> routeRuleFromJson(const QJsonObject& json) const;
        
        QJsonObject routeProfileToJson(const RouteProfile* routeProfile) const;
        
        std::shared_ptr<RouteProfile> routeProfileFromJson(const QJsonObject& json) const;
        
        void saveToDatabase(const RouteProfile* routeProfile, int id) const;

        void saveToDatabaseInTx(const RouteProfile* routeProfile, int id) const;
        
        std::shared_ptr<RouteProfile> loadFromDatabase(int id) const;
        
        std::shared_ptr<RouteProfile> routeProfileFromProfileRow(SQLite::Statement& stmt) const;
        
        QJsonObject ruleJsonFromRow(SQLite::Statement& stmt, int baseCol) const;

        void loadRulesForProfileIdsChunk(const QList<int>& profileIds, std::map<int, std::shared_ptr<RouteProfile>>& byId) const;
        
        void createTables() const;

        [[nodiscard]] bool routeRulesColumnExists(const char* columnName) const;

        [[nodiscard]] bool routeProfilesColumnExists(const char* columnName) const;

        int NewRouteProfileID() const;

    public:
        explicit RoutesRepo(Database& database);
        
        [[nodiscard]] static std::shared_ptr<RouteProfile> NewRouteProfile();
        
        bool AddRouteProfile(std::shared_ptr<RouteProfile>& routeProfile);
        
        std::shared_ptr<RouteProfile> GetRouteProfile(int id) const;
        
        void DeleteRouteProfile(int id);
        
        void UpdateRouteProfiles(const QList<std::shared_ptr<RouteProfile>>& routeProfiles);
        
        QList<int> GetAllRouteProfileIds() const;

        QList<std::shared_ptr<RouteProfile>> GetAllRouteProfiles() const;
        
        bool Save(const std::shared_ptr<RouteProfile>& routeProfile);
    };
}
