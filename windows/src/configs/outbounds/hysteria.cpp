#include "include/configs/outbounds/hysteria.h"

#include <QJsonArray>
#include <QUrlQuery>
#include <include/global/Utils.hpp>

#include "include/configs/common/utils.h"

namespace Configs {
    QStringList portsToPorts(const QStringList &ports) {
        QStringList result;
        
        for (const QString &v : ports) {
            QStringList range = v.split(QRegularExpression("[:-]"));

            if (range.size() == 2) {
                result.append(QString("%1:%2").arg(range[0], range[1]));
            } else {
                result.append(QString("%1:%2").arg(v, v));
            }
        }
        
        return result;
    }

    static QJsonObject hysteria2Obfs(const hysteria *h) {
        QJsonObject obfsObj{
            {"type", h->obfs_type == "gecko" ? "gecko" : "salamander"},
            {"password", h->obfs},
        };
        if (h->obfs_type == "gecko") {
            if (h->obfs_min_packet_size > 0) obfsObj["min_packet_size"] = h->obfs_min_packet_size;
            if (h->obfs_max_packet_size > 0) obfsObj["max_packet_size"] = h->obfs_max_packet_size;
        }
        return obfsObj;
    }

    static QString hopDuration(const QString &v) {
        static const QRegularExpression bareNumber("^\\d+$");
        auto t = v.trimmed();
        return bareNumber.match(t).hasMatch() ? t + "s" : t;
    }

    bool hysteria::ParseFromLink(const QString& link)
    {
        auto url = QUrl(link);
        if (!url.isValid())
        {
            if(!url.errorString().startsWith("Invalid port"))
                return false;
            QString authority = url.authority();
            int portStartIndex = link.indexOf(authority) + authority.length();
            QRegularExpressionMatch match = QRegularExpression("^:([\\d,\\-]+)").match(link.mid(portStartIndex));
            if (match.hasMatch()) {
                server_ports = portsToPorts(match.captured(1).split(","));
            }
        }
        auto query = QUrlQuery(url.query());

        outbound::ParseFromLink(link);
        
        if (url.scheme() == "hysteria") {
            protocol_version = "1";
            if (query.hasQueryItem("obfsParam")) obfs = query.queryItemValue("obfsParam", QUrl::FullyDecoded);
            if (query.hasQueryItem("auth")) {
                auth = query.queryItemValue("auth");
                auth_type = "STRING";
            }
            if (query.hasQueryItem("recv_window_conn")) recv_window_conn = query.queryItemValue("recv_window_conn").toInt();
            if (query.hasQueryItem("recv_window")) recv_window = query.queryItemValue("recv_window").toInt();
            if (query.hasQueryItem("disable_mtu_discovery")) disable_mtu_discovery = query.queryItemValue("disable_mtu_discovery") == "true";
        } else {
            protocol_version = "2";
            if (url.password().isEmpty()) {
                password = url.userName();
            } else {
                password = url.userName() + ":" + url.password();
            }
            if (query.hasQueryItem("obfs-password")) {
                obfs = query.queryItemValue("obfs-password", QUrl::FullyDecoded);
            }
            if (query.queryItemValue("obfs") == "gecko") obfs_type = "gecko";
            if (query.hasQueryItem("obfs-min-packet-size")) obfs_min_packet_size = query.queryItemValue("obfs-min-packet-size").toInt();
            if (query.hasQueryItem("obfs-max-packet-size")) obfs_max_packet_size = query.queryItemValue("obfs-max-packet-size").toInt();
            if (query.hasQueryItem("hop-interval-max")) hop_interval_max = query.queryItemValue("hop-interval-max");
            if (query.hasQueryItem("hop_interval_max")) hop_interval_max = query.queryItemValue("hop_interval_max");
            if (query.hasQueryItem("bbr_profile")) bbr_profile = query.queryItemValue("bbr_profile");
            if (query.hasQueryItem("disable_chrome_parrot")) disable_chrome_parrot = query.queryItemValue("disable_chrome_parrot").replace("1", "true") == "true";
        }
        
        if (query.hasQueryItem("upmbps")) up_mbps = query.queryItemValue("upmbps").toInt();
        if (query.hasQueryItem("downmbps")) down_mbps = query.queryItemValue("downmbps").toInt();
        if (query.hasQueryItem("hop-interval")) hop_interval = query.queryItemValue("hop-interval");
        if (query.hasQueryItem("hop_interval")) hop_interval = query.queryItemValue("hop_interval");
        if (query.hasQueryItem("mport")) {
            server_ports = portsToPorts(query.queryItemValue("mport", QUrl::FullyDecoded).split(","));
        }
        
        tls->ParseFromLink(link);
        tls->enabled = true;
        
        if (server_port == 0 && server_ports.isEmpty()) server_port = 443;

        return true;
    }

    bool hysteria::ParseFromJson(const QJsonObject& object)
    {
        if (object.isEmpty()) return false;
        if (object["type"].toString() == "hysteria") {
            protocol_version = "1";
        } else if (object["type"].toString() == "hysteria2") {
            protocol_version = "2";
        } else {
            return false;
        }
        outbound::ParseFromJson(object);
        if (object.contains("server_ports")) {
            server_ports = QJsonArray2QListString(object["server_ports"].toArray());
        }
        if (object.contains("hop_interval")) hop_interval = object["hop_interval"].toString();
        if (object.contains("up_mbps")) up_mbps = object["up_mbps"].toInt();
        if (object.contains("down_mbps")) down_mbps = object["down_mbps"].toInt();
        if (protocol_version == "1") {
            if (object.contains("obfs")) obfs = object["obfs"].toString();
            if (object.contains("auth")) {
                auth = object["auth"].toString();
                auth_type = "BASE64";
            }
            if (object.contains("auth_str")) {
                auth = object["auth_str"].toString();
                auth_type = "STRING";
            }
            if (object.contains("recv_window_conn")) recv_window_conn = object["recv_window_conn"].toInt();
            if (object.contains("recv_window")) recv_window = object["recv_window"].toInt();
            if (object.contains("disable_mtu_discovery")) disable_mtu_discovery = object["disable_mtu_discovery"].toBool();
            if (object["connection_receive_window"].isDouble()) recv_window_conn = object["connection_receive_window"].toInt();
            if (object["stream_receive_window"].isDouble()) recv_window = object["stream_receive_window"].toInt();
            if (object.contains("disable_path_mtu_discovery")) disable_mtu_discovery = object["disable_path_mtu_discovery"].toBool();
        } else {
            if (object.contains("obfs")) {
                auto obfsObj = object["obfs"].toObject();
                if (obfsObj.contains("password")) obfs = obfsObj["password"].toString();
                if (obfsObj["type"].toString() == "gecko") {
                    obfs_type = "gecko";
                    if (obfsObj.contains("min_packet_size")) obfs_min_packet_size = obfsObj["min_packet_size"].toInt();
                    if (obfsObj.contains("max_packet_size")) obfs_max_packet_size = obfsObj["max_packet_size"].toInt();
                }
            }
            if (object.contains("obfsPassword")) obfs = object["obfsPassword"].toString();
            if (object.contains("password")) password = object["password"].toString();
            if (object.contains("hop_interval_max")) hop_interval_max = object["hop_interval_max"].toString();
            if (object.contains("bbr_profile")) bbr_profile = object["bbr_profile"].toString();
            if (object.contains("disable_chrome_parrot")) disable_chrome_parrot = object["disable_chrome_parrot"].toBool();
        }
        if (object.contains("tls")) tls->ParseFromJson(object["tls"].toObject());
        return true;
    }

    bool hysteria::ParseFromClash(const clash::Proxies& object)
    {
        if (object.type == "hysteria") {
            protocol_version = "1";
        } else if (object.type == "hysteria2") {
            protocol_version = "2";
        } else {
            return false;
        }
        outbound::ParseFromClash(object);

        if (!object.ports.empty()) server_ports = portsToPorts(QString::fromStdString(object.ports).split(QRegularExpression("[,/]"), Qt::SkipEmptyParts));
        if (!object.hop_interval.empty()) hop_interval = QString::fromStdString(object.hop_interval);
        auto anyToMbps = [](const QString &s) -> int {
            if (s.isEmpty()) return 0;

            bool ok;
            int directMb = s.toInt(&ok);
            if (ok) return directMb;

            static QRegularExpression re(R"(^(\d+)([KMGT]?)([Bb]?))");
            QRegularExpressionMatch match = re.match(s);

            if (!match.hasMatch()) return 0;

            double v = match.captured(1).toDouble();
            QString unit = match.captured(2).toUpper();
            QString type = match.captured(3).toUpper();

            double n = 1.0;

            if (unit == "K")      n = 0.001;
            else if (unit == "M") n = 1.0;
            else if (unit == "G") n = 1000.0;
            else if (unit == "T") n = 1000000.0;

            if (type == "B") {
                n *= 8.0;
            }

            return static_cast<int>(v * n);
        };
        if (!object.up.empty()) up_mbps = anyToMbps(QString::fromStdString(object.up));
        if (!object.down.empty()) down_mbps = anyToMbps(QString::fromStdString(object.down));
        if (protocol_version == "1") {
            if (!object.auth_str.empty()) {
                auth = QString::fromStdString(object.auth_str);
                auth_type = "STRING";
            } else if (!object.auth_str1.empty()) {
                auth = QString::fromStdString(object.auth_str1);
                auth_type = "STRING";
            }
            if (!object.obfs.empty()) obfs = QString::fromStdString(object.obfs);
            if (object.recv_window > 0) {
                recv_window = object.recv_window;
            } else if (object.recv_window1 > 0) {
                recv_window = object.recv_window1;
            }
            if (object.recv_window_conn > 0) {
                recv_window_conn = object.recv_window_conn;
            } else if (object.recv_window_conn1 > 0) {
                recv_window_conn = object.recv_window_conn1;
            }
            disable_mtu_discovery = object.disable_mtu_discovery;
        } else {
            if (!object.password.empty()) password = QString::fromStdString(object.password);
            if (!object.obfs_password.empty()) obfs = QString::fromStdString(object.obfs_password);
        }

        tls->ParseFromClash(object);
        tls->enabled = true;
        return true;
    }

    QString hysteria::ExportToLink()
    {
        QUrl url;
        QUrlQuery query;
        url.setScheme(protocol_version == "1" ? "hysteria" : "hysteria2");
        url.setHost(server);
        
        if (!name.isEmpty()) url.setFragment(name);

        if (protocol_version == "1") {
            if (!obfs.isEmpty()) {
                query.addQueryItem("obfsParam", QUrl::toPercentEncoding(obfs));
            }
            if (auth_type == "STRING" && !auth.isEmpty()) query.addQueryItem("auth", auth);
            if (recv_window_conn > 0) query.addQueryItem("recv_window_conn", QString::number(recv_window_conn));
            if (recv_window > 0) query.addQueryItem("recv_window", QString::number(recv_window));
            if (disable_mtu_discovery) query.addQueryItem("disable_mtu_discovery", "true");
        } else {
            if (password.contains(":")) {
                url.setUserName(SubStrBefore(password, ":"));
                url.setPassword(SubStrAfter(password, ":"));
            } else {
                url.setUserName(password);
            }
            if (!obfs.isEmpty()) {
                query.addQueryItem("obfs", obfs_type == "gecko" ? "gecko" : "salamander");
                query.addQueryItem("obfs-password", QUrl::toPercentEncoding(obfs));
                if (obfs_type == "gecko") {
                    if (obfs_min_packet_size > 0) query.addQueryItem("obfs-min-packet-size", QString::number(obfs_min_packet_size));
                    if (obfs_max_packet_size > 0) query.addQueryItem("obfs-max-packet-size", QString::number(obfs_max_packet_size));
                }
            }
            if (!hop_interval_max.isEmpty()) query.addQueryItem("hop_interval_max", hop_interval_max);
            if (!bbr_profile.isEmpty()) query.addQueryItem("bbr_profile", bbr_profile);
            if (disable_chrome_parrot) query.addQueryItem("disable_chrome_parrot", "true");
        }
        
        if (up_mbps > 0) query.addQueryItem("upmbps", QString::number(up_mbps));
        if (down_mbps > 0) query.addQueryItem("downmbps", QString::number(down_mbps));
        
        if (!hop_interval.isEmpty()) query.addQueryItem("hop_interval", hop_interval);
        
        mergeUrlQuery(query, tls->ExportToLink());
        mergeUrlQuery(query, outbound::ExportToLink());
        
        if (!query.isEmpty()) url.setQuery(query);
        
        QString result;
        if (!server_ports.isEmpty()) {
            QStringList portList;
            for (const auto& port : server_ports) {
                QString modified = port;
                modified.replace(":", "-");
                portList.append(modified);
            }
            result = url.toString(QUrl::FullyEncoded);
            QString authority = url.authority(QUrl::FullyEncoded);
            int portStartIndex = result.indexOf(authority) + authority.length();
            result.insert(portStartIndex, ":" + portList.join(","));
        } else {
            url.setPort(server_port);
            result = url.toString(QUrl::FullyEncoded);
        }
        
        return result;
    }

    QJsonObject hysteria::ExportToJson()
    {
        QJsonObject object;
        object["type"] = protocol_version == "1" ? "hysteria" : "hysteria2";
        mergeJsonObjects(object, outbound::ExportToJson());
        if (!server_ports.isEmpty()) object["server_ports"] = QListStr2QJsonArray(portsToPorts(server_ports));
        if (!hop_interval.isEmpty()) object["hop_interval"] = hopDuration(hop_interval);
        if (up_mbps > 0) object["up_mbps"] = up_mbps;
        if (down_mbps > 0) object["down_mbps"] = down_mbps;
        if (protocol_version == "1") {
            if (!obfs.isEmpty()) object["obfs"] = obfs;
            if (!auth.isEmpty()) {
                if (auth_type == "BASE64") object["auth"] = auth;
                if (auth_type == "STRING") object["auth_str"] = auth;
            }
            if (recv_window_conn > 0) object["connection_receive_window"] = recv_window_conn;
            if (recv_window > 0) object["stream_receive_window"] = recv_window;
            if (disable_mtu_discovery) object["disable_path_mtu_discovery"] = true;
        } else {
            if (!obfs.isEmpty()) object["obfs"] = hysteria2Obfs(this);
            if (!password.isEmpty()) object["password"] = password;
            if (!hop_interval_max.isEmpty()) object["hop_interval_max"] = hopDuration(hop_interval_max);
            if (!bbr_profile.isEmpty()) object["bbr_profile"] = bbr_profile;
            if (disable_chrome_parrot) object["disable_chrome_parrot"] = true;
        }
        object["tls"] = tls->ExportToJson();
        return object;
    }

    QJsonObject hysteria::ExportIdentity()
    {
        auto object = outbound::ExportIdentity();
        object["protocol_version"] = protocol_version;
        if (!server_ports.isEmpty()) object["server_ports"] = server_ports.join(",");
        if (!obfs.isEmpty()) object["obfs"] = true;
        return object;
    }

    BuildResult hysteria::Build()
    {
        QJsonObject object;
        object["type"] = protocol_version == "1" ? "hysteria" : "hysteria2";
        mergeJsonObjects(object, outbound::Build().object);
        if (!server_ports.isEmpty()) object["server_ports"] = QListStr2QJsonArray(portsToPorts(server_ports));
        if (!hop_interval.isEmpty()) object["hop_interval"] = hopDuration(hop_interval);
        if (up_mbps > 0) object["up_mbps"] = up_mbps;
        if (down_mbps > 0) object["down_mbps"] = down_mbps;
        if (protocol_version == "1") {
            if (!obfs.isEmpty()) object["obfs"] = obfs;
            if (!auth.isEmpty()) {
                if (auth_type == "BASE64") object["auth"] = auth;
                if (auth_type == "STRING") object["auth_str"] = auth;
            }
            if (recv_window_conn > 0) object["connection_receive_window"] = recv_window_conn;
            if (recv_window > 0) object["stream_receive_window"] = recv_window;
            if (disable_mtu_discovery) object["disable_path_mtu_discovery"] = true;
        } else {
            if (!obfs.isEmpty()) object["obfs"] = hysteria2Obfs(this);
            if (!password.isEmpty()) object["password"] = password;
            if (!hop_interval_max.isEmpty()) object["hop_interval_max"] = hopDuration(hop_interval_max);
            if (!bbr_profile.isEmpty()) object["bbr_profile"] = bbr_profile;
            if (disable_chrome_parrot) object["disable_chrome_parrot"] = true;
        }
        object["tls"] = tls->Build().object;
        return {object, ""};
    }

    QString hysteria::DisplayType()
    {
        return protocol_version == "1" ? "Hysteria" : "Hysteria2";
    }

    SecurityInfo hysteria::GetSecurity()
    {
        return SecurityFromTLS("QUIC");
    }
}
