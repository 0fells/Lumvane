#pragma once

#include <QString>
#include <memory>

#include "include/database/entities/RouteProfile.h"

namespace RouteUpdate {
    QString UpdateProfile(const std::shared_ptr<Configs::RouteProfile>& profile, QString* warnings = nullptr);
}

void UI_update_all_remote_routes(bool onlyAllowed = false);
