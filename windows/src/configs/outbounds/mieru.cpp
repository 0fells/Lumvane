#include "include/configs/outbounds/mieru.h"

#include <QJsonArray>
#include <QUrlQuery>
#include <include/global/Utils.hpp>

#include "include/configs/common/utils.h"

namespace Configs {
    namespace {
        QJsonArray splitServerPorts(const QString& raw) {
            QJsonArray arr;
            for (auto part : raw.split(',', Qt::SkipEmptyParts)) {
                part = part.trimmed();
                if (!part.isEmpty()) arr.append(part);
            }
            return arr;
        }
    }

    bool mieru::ParseFromLink(const QString& link)
    {
        auto url = QUrl(link);
        if (!url.isValid() || url.host().isEmpty() || url.query().isEmpty()) return false;
        auto query = QUrlQuery(url.query());

        outbound::ParseFromLink(link);
        username = url.userName();
        password = url.password();

        const auto protocols = query.allQueryItemValues("protocol");
        if (!protocols.isEmpty()) transport = protocols.first().toUpper();

        QStringList ranges;
        bool haveSinglePort = false;
        server_port = 0;
        for (auto port : query.allQueryItemValues("port")) {
            port = port.trimmed();
            if (port.isEmpty()) continue;
            if (port.contains('-')) {
                ranges << port;
            } else if (!haveSinglePort) {
                server_port = port.toInt();
                haveSinglePort = true;
            } else {
                ranges << QStringLiteral("%1-%1").arg(port);
            }
        }
        server_ports = ranges.join(",");

        if (query.hasQueryItem("multiplexing")) multiplexing = query.queryItemValue("multiplexing");
        if (query.hasQueryItem("traffic-pattern")) traffic_pattern = query.queryItemValue("traffic-pattern");

        return true;
    }

    bool mieru::ParseFromJson(const QJsonObject& object)
    {
        if (object.isEmpty() || object["type"].toString() != "mieru") return false;
        outbound::ParseFromJson(object);
        if (object.contains("transport")) transport = object["transport"].toString();
        if (object.contains("username")) username = object["username"].toString();
        if (object.contains("password")) password = object["password"].toString();
        if (object.contains("multiplexing")) multiplexing = object["multiplexing"].toString();
        if (object.contains("traffic_pattern")) traffic_pattern = object["traffic_pattern"].toString();
        if (object.contains("server_ports")) {
            QStringList ports;
            for (const auto& v : object["server_ports"].toArray()) ports << v.toString();
            server_ports = ports.join(",");
        }
        return true;
    }

    bool mieru::ParseFromClash(const clash::Proxies& object)
    {
        return false;
    }

    QString mieru::ExportToLink()
    {
        QUrl url;
        QUrlQuery query;
        url.setScheme("mierus");
        url.setUserName(username);
        url.setPassword(password);
        url.setHost(server);
        if (!name.isEmpty()) url.setFragment(name);

        query.addQueryItem("profile", "default");

        const auto protocol = transport.isEmpty() ? QStringLiteral("TCP") : transport.toUpper();
        auto addPort = [&](const QString& port) {
            query.addQueryItem("port", port);
            query.addQueryItem("protocol", protocol);
        };
        if (server_port > 0) addPort(QString::number(server_port));
        for (auto part : server_ports.split(',', Qt::SkipEmptyParts)) {
            part = part.trimmed();
            if (!part.isEmpty()) addPort(part);
        }

        if (!multiplexing.isEmpty()) query.addQueryItem("multiplexing", multiplexing);
        if (!traffic_pattern.isEmpty()) query.addQueryItem("traffic-pattern", traffic_pattern);

        mergeUrlQuery(query, outbound::ExportToLink());

        if (!query.isEmpty()) url.setQuery(query);
        return url.toString(QUrl::FullyEncoded);
    }

    QJsonObject mieru::ExportToJson()
    {
        QJsonObject object;
        object["type"] = "mieru";
        mergeJsonObjects(object, outbound::ExportToJson());
        object["transport"] = transport.isEmpty() ? QString("TCP") : transport;
        if (!username.isEmpty()) object["username"] = username;
        if (!password.isEmpty()) object["password"] = password;
        if (!multiplexing.isEmpty()) object["multiplexing"] = multiplexing;
        if (!traffic_pattern.isEmpty()) object["traffic_pattern"] = traffic_pattern;
        auto ports = splitServerPorts(server_ports);
        if (!ports.isEmpty()) object["server_ports"] = ports;
        return object;
    }

    BuildResult mieru::Build()
    {
        QJsonObject object;
        object["type"] = "mieru";
        mergeJsonObjects(object, outbound::Build().object);
        object["transport"] = transport.isEmpty() ? QString("TCP") : transport;
        if (!username.isEmpty()) object["username"] = username;
        if (!password.isEmpty()) object["password"] = password;
        if (!multiplexing.isEmpty()) object["multiplexing"] = multiplexing;
        if (!traffic_pattern.isEmpty()) object["traffic_pattern"] = traffic_pattern;
        auto ports = splitServerPorts(server_ports);
        if (!ports.isEmpty()) object["server_ports"] = ports;
        return {object, ""};
    }

    QString mieru::DisplayType()
    {
        return "Mieru";
    }

    SecurityInfo mieru::GetSecurity()
    {
        return {QObject::tr("Encrypted"), {}, SecurityLevel::Secure};
    }
}
