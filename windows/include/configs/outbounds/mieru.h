#pragma once
#include "include/configs/common/Outbound.h"

namespace Configs
{
    inline QStringList mieruTransports = {"TCP", "UDP"};
    inline QStringList mieruMultiplexing = {"", "MULTIPLEXING_OFF", "MULTIPLEXING_LOW", "MULTIPLEXING_MIDDLE", "MULTIPLEXING_HIGH"};

    class mieru : public outbound
    {
        public:
        QString transport = "TCP";
        QString username;
        QString password;
        QString multiplexing;
        QString traffic_pattern;
        QString server_ports;

        bool ParseFromLink(const QString& link) override;
        bool ParseFromJson(const QJsonObject& object) override;
        bool ParseFromClash(const clash::Proxies& object) override;
        QString ExportToLink() override;
        QJsonObject ExportToJson() override;
        BuildResult Build() override;

        QString DisplayType() override;
        SecurityInfo GetSecurity() override;
    };
}
