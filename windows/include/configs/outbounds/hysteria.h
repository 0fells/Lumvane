#pragma once
#include "include/configs/common/Outbound.h"
#include "include/configs/common/TLS.h"

namespace Configs
{
    class hysteria : public outbound
    {
        public:
        QString protocol_version = "1";
        QStringList server_ports;
        QString hop_interval;
        int up_mbps = 0;
        int down_mbps = 0;
        QString obfs;

        QString auth_type;
        QString auth;
        int recv_window_conn = 0;
        int recv_window = 0;
        bool disable_mtu_discovery = false;

        QString password;
        QString hop_interval_max;
        QString obfs_type;
        int obfs_min_packet_size = 0;
        int obfs_max_packet_size = 0;
        QString bbr_profile;
        bool disable_chrome_parrot = false;

        inline static const QStringList obfsTypes = {"salamander", "gecko"};
        inline static const QStringList bbrProfiles = {"", "conservative", "standard", "aggressive"};

        std::shared_ptr<TLS> tls = std::make_shared<TLS>();

        hysteria()
        {
            tls->utls->supported = false;
            tls->spoof_supported = false;
            tls->fragment_supported = false;
            tls->reality_supported = false;
        }

        bool HasTLS() override {
            return true;
        }

        bool MustTLS() override {
            return true;
        }

        std::shared_ptr<TLS> GetTLS() override {
            return tls;
        }

        bool ParseFromLink(const QString& link) override;
        bool ParseFromJson(const QJsonObject& object) override;
        bool ParseFromClash(const clash::Proxies& object) override;
        QString ExportToLink() override;
        QJsonObject ExportToJson() override;
        QJsonObject ExportIdentity() override;
        BuildResult Build() override;

        QString DisplayType() override;
        SecurityInfo GetSecurity() override;
    };
}
