#pragma once
#include "include/configs/baseConfig.h"

namespace Configs
{

    inline QStringList tlsFingerprints = {"", "chrome", "firefox", "edge", "safari", "360", "qq", "ios", "android", "random", "randomized"};
    inline QStringList tlsSpoofMethods = {"", "wrong-sequence", "wrong-checksum", "wrong-ack", "wrong-md5", "wrong-timestamp"};

    class uTLS : public baseConfig
    {
        public:
        bool supported = true;
        bool enabled = false;
        QString fingerPrint;

        bool ParseFromLink(const QString& link) override;
        bool ParseFromJson(const QJsonObject& object) override;
        bool ParseFromClash(const clash::Proxies& object) override;
        QString ExportToLink() override;
        QJsonObject ExportToJson() override;
        QJsonObject ExportIdentity() override;
        BuildResult Build() override;
    };

    class ECH : public baseConfig
    {
        public:
        bool enabled = false;
        QStringList config;
        QString config_path;
        QString serverName;

        bool ParseFromLink(const QString& link) override;
        bool ParseFromJson(const QJsonObject& object) override;
        QString ExportToLink() override;
        QJsonObject ExportToJson() override;
        QJsonObject ExportIdentity() override;
        BuildResult Build() override;
    };

    class Reality : public baseConfig
    {
        public:
        bool enabled = false;
        QString public_key;
        QString short_id;

        bool ParseFromLink(const QString& link) override;
        bool ParseFromJson(const QJsonObject& object) override;
        bool ParseFromClash(const clash::Proxies& object) override;
        QString ExportToLink() override;
        QJsonObject ExportToJson() override;
        QJsonObject ExportIdentity() override;
        BuildResult Build() override;
    };

    class TLS : public baseConfig
    {
        public:
        bool enabled = false;
        bool disable_sni = false;
        QString server_name;
        bool insecure = false;
        QStringList alpn;
        QString min_version;
        QString max_version;
        QStringList cipher_suites;
        QStringList curve_preferences;
        QStringList certificate;
        QString certificate_path;
        QStringList certificate_public_key_sha256;
        QStringList client_certificate;
        QString client_certificate_path;
        QStringList client_key;
        QString client_key_path;
        bool fragment = false;
        bool fragment_unspecified = true;
        QString fragment_fallback_delay;
        bool record_fragment = false;
        bool tls_tricks = false;
        bool tls_tricks_unspecified = true;
        bool spoof_supported = true;
        bool fragment_supported = true;
        bool reality_supported = true;
        bool tricks_supported = true;
        bool insecure_alpn_supported = true;
        QString spoof;
        QString spoof_method;
        std::shared_ptr<ECH> ech = std::make_shared<ECH>();
        std::shared_ptr<uTLS> utls = std::make_shared<uTLS>();
        std::shared_ptr<Reality> reality = std::make_shared<Reality>();

        int getFragmentState() {
            if (fragment) return 1;
            if (fragment_unspecified) return 0;
            return 2;
        }
        void saveFragmentState(int state) {
            fragment = state == 1;
            fragment_unspecified = state == 0;
        }
        int getTlsTricksState() {
            if (tls_tricks) return 1;
            if (tls_tricks_unspecified) return 0;
            return 2;
        }
        void saveTlsTricksState(int state) {
            tls_tricks = state == 1;
            tls_tricks_unspecified = state == 0;
        }
        bool FragmentEffectivelyOn();
        bool TlsTricksEffectivelyOn();

        bool ParseFromLink(const QString& link) override;
        bool ParseFromJson(const QJsonObject& object) override;
        bool ParseFromClash(const clash::Proxies& object) override;
        QString ExportToLink() override;
        QJsonObject ExportToJson() override;
        QJsonObject ExportIdentity() override;
        BuildResult Build() override;
    };
}
