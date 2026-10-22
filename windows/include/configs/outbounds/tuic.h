#pragma once
#include "include/configs/common/Outbound.h"
#include "include/configs/common/TLS.h"

namespace Configs
{

    inline QStringList ccAlgorithms = {"cubic", "new_reno", "bbr"};
    inline QStringList udpRelayModes = {"", "native", "quic"};

    class tuic : public outbound
    {
        public:
        QString uuid;
        QString password;
        QString congestion_control;
        QString udp_relay_mode;
        bool udp_over_stream = false;
        bool zero_rtt_handshake = false;
        QString heartbeat;
        std::shared_ptr<TLS> tls = std::make_shared<TLS>();

        tuic()
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
        BuildResult Build() override;

        QString DisplayType() override;
        SecurityInfo GetSecurity() override;
    };
}
