#pragma once
#include <QString>

namespace Configs
{
    class outbound;

    outbound* NewOutboundByType(const QString& type);
}
