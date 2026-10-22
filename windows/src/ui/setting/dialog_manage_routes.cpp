#include "include/ui/setting/dialog_manage_routes.h"

#include <QClipboard>

#include "include/ui/widget/JsonEditor.hpp"
#include "include/global/GuiUtils.hpp"

#include <QFile>
#include <QMessageBox>
#include <QShortcut>
#include <QTimer>
#include <QToolTip>
#include <QDialog>
#include <QTextEdit>
#include <QGridLayout>
#include <QDialogButtonBox>
#include <include/api/RPC.h>

#include "include/configs/sub/warp.h"
#include "include/configs/sub/RouteUpdater.hpp"

#include <srslist.h>
#include "include/database/RoutesRepo.h"
#include "include/ui/setting/RawRouteItem.h"

namespace {
    void fillStrategies(QComboBox *combo) {
        for (const auto &strategy : Configs::DomainStrategy::DomainStrategy)
            combo->addItem(strategy.isEmpty() ? DialogManageRoutes::tr("Default") : strategy, strategy);
    }
    void selectStrategy(QComboBox *combo, const QString &strategy) {
        combo->setCurrentIndex(qMax(0, combo->findData(strategy)));
    }
    QString selectedStrategy(const QComboBox *combo) { return combo->currentData().toString(); }
}

void DialogManageRoutes::reloadProfileItems() {
    if (chainList.empty()) {
        MessageBoxWarning(tr("Invalid state"), tr("The list of routing profiles is empty, this should be an unreachable state, crashes may occur now"));
        return;
    }

    QSignalBlocker blocker = QSignalBlocker(ui->route_prof);
    ui->route_prof->clear();

    ui->route_profiles->clear();
    bool selectedChainGone = true;
    int i=0;
    for (const auto &item: chainList) {
        ui->route_prof->addItem(item->name);
        ui->route_profiles->addItem(item->name);
        if (item == currentRoute) {
            ui->route_prof->setCurrentIndex(i);
            selectedChainGone=false;
        }
        i++;
    }
    if (selectedChainGone) {
        currentRoute=chainList[0];
        ui->route_prof->setCurrentIndex(0);
    }
    blocker.unblock();
}

void DialogManageRoutes::set_dns_hijack_enability(const bool enable) const {
    ui->dnshijack_allow_lan->setEnabled(enable);
    ui->dnshijack_listenport->setEnabled(enable);
    ui->dnshijack_rules->setEnabled(enable);
    ui->dnshijack_v4resp->setEnabled(enable);
    ui->dnshijack_v6resp->setEnabled(enable);
}

bool DialogManageRoutes::validate_dns_rules(const QString &rawString) {
    auto rules = rawString.split("\n");
    for (const auto& rule : rules) {
        if (!rule.trimmed().isEmpty() && !rule.startsWith("ruleset:") && !rule.startsWith("domain:") && !rule.startsWith("suffix:") && !rule.startsWith("regex:")) return false;
    }
    return true;
}

DialogManageRoutes::DialogManageRoutes(QWidget *parent) : QDialog(parent), ui(new Ui::DialogManageRoutes) {
    ui->setupUi(this);
    auto profiles = Configs::dataManager->routesRepo->GetAllRouteProfiles();
    for (const auto &item: profiles) {
        chainList << item;
    }
    if (chainList.empty()) {
        auto defaultChain = Configs::RouteProfile::GetDefaultChain();
        Configs::dataManager->routesRepo->AddRouteProfile(defaultChain);
        chainList.append(defaultChain);
    }
    currentRoute = Configs::dataManager->routesRepo->GetRouteProfile(Configs::dataManager->settingsRepo->current_route_id);
    if (currentRoute == nullptr) currentRoute = chainList[0];

    QString dnsHelpDocumentUrl;

    ui->route_profiles->setSpacing(3);
    fillStrategies(ui->default_domain_strategy);
    fillStrategies(ui->domainStrategyCombo);
    ui->dns_object->setPlaceholderText(QStringLiteral("{\n  \"servers\": [],\n  \"rules\": [],\n  \"final\": \"\",\n  \"strategy\": \"\",\n  \"disable_cache\": false,\n  \"disable_expire\": false,\n  \"reverse_mapping\": false\n}"));
    dnsHelpDocumentUrl = "https://sing-box.sagernet.org/configuration/dns/";

    fillStrategies(ui->direct_dns_strategy);
    fillStrategies(ui->remote_dns_strategy);
    ui->local_override->setText(Configs::dataManager->settingsRepo->core_box_underlying_dns);
    ui->cache_cap->setText(Int2String(Configs::dataManager->settingsRepo->dns_cache_capacity));
    ui->disable_cache->setChecked(Configs::dataManager->settingsRepo->dns_disable_cache);
    ui->disable_expire->setChecked(Configs::dataManager->settingsRepo->dns_disable_expire);
    ui->optimistic_cache->setChecked(Configs::dataManager->settingsRepo->dns_optimistic_cache);
    auto syncOptimisticCache = [this] {
        ui->optimistic_cache->setEnabled(!ui->disable_cache->isChecked() && !ui->disable_expire->isChecked());
    };
    connect(ui->disable_cache, &QCheckBox::toggled, this, syncOptimisticCache);
    connect(ui->disable_expire, &QCheckBox::toggled, this, syncOptimisticCache);
    syncOptimisticCache();
    ui->reverse_mapping->setChecked(Configs::dataManager->settingsRepo->dns_reverse_mapping);
    ui->enable_fakeip->setChecked(Configs::dataManager->settingsRepo->fake_dns);
    connect(ui->use_dns_object, &QCheckBox::stateChanged, this, [=,this](int state) {
        auto useDNSObject = state == Qt::Checked;
        ui->simple_dns_box->setDisabled(useDNSObject);
        ui->dns_object->setDisabled(!useDNSObject);
    });
    ui->use_dns_object->stateChanged(Qt::Unchecked);
    connect(ui->dns_document, &QPushButton::clicked, this, [=,this] {
        MessageBoxInfo("DNS", dnsHelpDocumentUrl);
    });
    connect(ui->format_dns_object, &QPushButton::clicked, this, [=,this] {
        auto obj = QString2QJsonObject(ui->dns_object->toPlainText());
        if (obj.isEmpty()) {
            MessageBoxInfo("DNS", "Invalid json");
        } else {
            ui->dns_object->setPlainText(QJsonObject2QString(obj, false));
        }
    });
    ui->ruleset_mirror->setCurrentIndex(Configs::dataManager->settingsRepo->ruleset_mirror);
    selectStrategy(ui->default_domain_strategy, Configs::dataManager->settingsRepo->default_domain_strategy);
    selectStrategy(ui->domainStrategyCombo, Configs::dataManager->settingsRepo->resolve_domain_strategy);
    ui->use_dns_object->setChecked(Configs::dataManager->settingsRepo->use_dns_object);
    ui->dns_object->setPlainText(Configs::dataManager->settingsRepo->dns_object);
    ui->remote_dns->setCurrentText(Configs::dataManager->settingsRepo->remote_dns);
    selectStrategy(ui->remote_dns_strategy, Configs::dataManager->settingsRepo->remote_dns_strategy);
    ui->direct_dns->setCurrentText(Configs::dataManager->settingsRepo->direct_dns);
    selectStrategy(ui->direct_dns_strategy, Configs::dataManager->settingsRepo->direct_dns_strategy);
    ui->dns_final_out->setCurrentText(Configs::dataManager->settingsRepo->dns_final_out);
    ui->enable_dns_routing->setChecked(Configs::dataManager->settingsRepo->enable_dns_routing);
    reloadProfileItems();

    connect(ui->route_profiles, &QListWidget::itemDoubleClicked, this, [=,this](const QListWidgetItem* item){
        on_edit_route_clicked();
    });

    connect(ui->route_prof, SIGNAL(currentIndexChanged(int)), this, SLOT(updateCurrentRouteProfile(int)));

    deleteShortcut = new QShortcut(QKeySequence(Qt::Key_Delete), this);

    connect(deleteShortcut, &QShortcut::activated, this, [=,this]{
        on_delete_route_clicked();
    });

    auto exportShortcut = new QShortcut(QKeySequence::Copy, ui->route_profiles);
    exportShortcut->setContext(Qt::WidgetWithChildrenShortcut);
    connect(exportShortcut, &QShortcut::activated, this, [=,this]{
        on_export_route_clicked();
    });

    auto importShortcut = new QShortcut(QKeySequence::Paste, ui->route_profiles);
    importShortcut->setContext(Qt::WidgetWithChildrenShortcut);
    connect(importShortcut, &QShortcut::activated, this, [=,this]{
        on_import_route_clicked();
    });

    ui->dnshijack_enable->setChecked(Configs::dataManager->settingsRepo->enable_dns_server);
    set_dns_hijack_enability(Configs::dataManager->settingsRepo->enable_dns_server);
    ui->dnshijack_allow_lan->setChecked(Configs::dataManager->settingsRepo->dns_server_listen_lan);
    ui->dnshijack_listenport->setValidator(QRegExpValidator_Number);
    ui->dnshijack_listenport->setText(Int2String(Configs::dataManager->settingsRepo->dns_server_listen_port));
    ui->dnshijack_v4resp->setText(Configs::dataManager->settingsRepo->dns_v4_resp);
    ui->dnshijack_v6resp->setText(Configs::dataManager->settingsRepo->dns_v6_resp);
    connect(ui->dnshijack_what, &QPushButton::clicked, this, [=,this] {
        MessageBoxInfo("What is this?", Configs::Information::HijackInfo);
    });

    QStringList ruleItems = {"domain:", "suffix:", "regex:"};
    for (const auto& item : ruleSetList) {
        ruleItems.append("ruleset:" + QString::fromUtf8(item.first.data(), item.first.size()));
    }
    rule_editor = new AutoCompleteTextEdit("", ruleItems, this);
    ui->hijack_box->layout()->replaceWidget(ui->dnshijack_rules, rule_editor);
    rule_editor->setPlainText(Configs::dataManager->settingsRepo->dns_server_rules.join("\n"));
    ui->dnshijack_rules->hide();
#ifndef Q_OS_LINUX
    ui->dnshijack_listenport->setVisible(false);
    ui->dnshijack_listenport_l->setVisible(false);
#endif

    ui->redirect_enable->setChecked(Configs::dataManager->settingsRepo->enable_redirect);
    ui->redirect_listenaddr->setEnabled(Configs::dataManager->settingsRepo->enable_redirect);
    ui->redirect_listenaddr->setText(Configs::dataManager->settingsRepo->redirect_listen_address);
    ui->redirect_listenport->setEnabled(Configs::dataManager->settingsRepo->enable_redirect);
    ui->redirect_listenport->setValidator(QRegExpValidator_Number);
    ui->redirect_listenport->setText(Int2String(Configs::dataManager->settingsRepo->redirect_listen_port));

    connect(ui->dnshijack_enable, &QCheckBox::stateChanged, this, [=,this](bool state) {
        set_dns_hijack_enability(state);
    });
    connect(ui->redirect_enable, &QCheckBox::stateChanged, this, [=,this](bool state) {
        ui->redirect_listenaddr->setEnabled(state);
        ui->redirect_listenport->setEnabled(state);
    });

    ui->enable_warp->setChecked(Configs::dataManager->settingsRepo->enable_warp);
    ui->warp_private_key->setText(Configs::dataManager->settingsRepo->warp_private_key);
    ui->warp_public_key->setText(Configs::dataManager->settingsRepo->warp_public_key);
    ui->warp_ifc_addrs->setText(Configs::dataManager->settingsRepo->warp_ifc_addrs.join(","));
    ui->warp_ep->setText(Configs::dataManager->settingsRepo->warp_ep);
    ui->warp_reserved->setText(Configs::dataManager->settingsRepo->warp_reserved.join(","));
    connect(ui->warp_autogen, &QPushButton::clicked, this, [=,this] {
        auto originalText = ui->warp_autogen->text();
        ui->warp_autogen->setEnabled(false);
        ui->warp_autogen->setText(tr("Getting keypair..."));
        bool ok;
        auto keyPair = API::defaultClient->GenWgKeyPair(&ok);
        if (!ok) {
            runOnUiThread([=] {
               MessageBoxWarning(tr("Failed to get key pair"), keyPair.error->c_str());
            });
            ui->warp_autogen->setText(originalText);
            ui->warp_autogen->setEnabled(true);
            return;
        }
        ui->warp_autogen->setText(tr("Generating config..."));
        QString error;
        auto conf = Configs_network::genWarpConfig(&error, keyPair.private_key->c_str(), keyPair.public_key->c_str());
        if (!error.isEmpty()) {
            runOnUiThread([=] {
                MessageBoxWarning(tr("Failed to generate Warp config"), error);
            });
            ui->warp_autogen->setText(originalText);
            ui->warp_autogen->setEnabled(true);
            return;
        }
        ui->warp_private_key->setText(conf->privateKey);
        ui->warp_public_key->setText(conf->publicKey);
        ui->warp_ep->setText(conf->endpoint);
        ui->warp_ifc_addrs->setText(conf->ipv4Address + "/32," + conf->ipv6Address + "/128");
        ui->warp_reserved->setText(QListInt2QListString(conf->reserved).join(","));
        ui->warp_autogen->setText(tr("Success!"));
        setTimeout([=,this] {
            ui->warp_autogen->setText(originalText);
            ui->warp_autogen->setEnabled(true);
        }, this, 2000);
    });

    ADD_ASTERISK(this)
}

void DialogManageRoutes::updateCurrentRouteProfile(int idx) {
    if (idx < 0 || idx >= chainList.size()) return;
    currentRoute = chainList[idx];
}

DialogManageRoutes::~DialogManageRoutes() {
    delete ui;
}

void DialogManageRoutes::accept() {
    if (chainList.empty()) {
        MessageBoxInfo(tr("Invalid settings"), tr("Routing profile cannot be empty"));
        return;
    }
    if (!validate_dns_rules(rule_editor->toPlainText())) {
        MessageBoxInfo(tr("Invalid settings"), tr("DNS Rules are not valid"));
        return;
    }

    Configs::dataManager->settingsRepo->ruleset_mirror = ui->ruleset_mirror->currentIndex();
    Configs::dataManager->settingsRepo->resolve_domain_strategy = selectedStrategy(ui->domainStrategyCombo);
    Configs::dataManager->settingsRepo->default_domain_strategy = selectedStrategy(ui->default_domain_strategy);
    Configs::dataManager->settingsRepo->use_dns_object = ui->use_dns_object->isChecked();
    Configs::dataManager->settingsRepo->dns_object = ui->dns_object->toPlainText();
    Configs::dataManager->settingsRepo->remote_dns = ui->remote_dns->currentText();
    Configs::dataManager->settingsRepo->remote_dns_strategy = selectedStrategy(ui->remote_dns_strategy);
    Configs::dataManager->settingsRepo->dns_cache_capacity = ui->cache_cap->text().toInt();
    Configs::dataManager->settingsRepo->dns_disable_cache = ui->disable_cache->isChecked();
    Configs::dataManager->settingsRepo->dns_disable_expire = ui->disable_expire->isChecked();
    Configs::dataManager->settingsRepo->dns_optimistic_cache = ui->optimistic_cache->isChecked();
    Configs::dataManager->settingsRepo->dns_reverse_mapping = ui->reverse_mapping->isChecked();
    Configs::dataManager->settingsRepo->direct_dns = ui->direct_dns->currentText();
    Configs::dataManager->settingsRepo->direct_dns_strategy = selectedStrategy(ui->direct_dns_strategy);
    Configs::dataManager->settingsRepo->core_box_underlying_dns = ui->local_override->text().trimmed();
    Configs::dataManager->settingsRepo->dns_final_out = ui->dns_final_out->currentText();
    Configs::dataManager->settingsRepo->fake_dns = ui->enable_fakeip->isChecked();
    Configs::dataManager->settingsRepo->enable_dns_routing = ui->enable_dns_routing->isChecked();

    Configs::dataManager->routesRepo->UpdateRouteProfiles(chainList);
    Configs::dataManager->settingsRepo->current_route_id = currentRoute->id;

    Configs::dataManager->settingsRepo->enable_dns_server = ui->dnshijack_enable->isChecked();
    Configs::dataManager->settingsRepo->dns_server_listen_port = ui->dnshijack_listenport->text().toInt();
    Configs::dataManager->settingsRepo->dns_v4_resp = ui->dnshijack_v4resp->text();
    Configs::dataManager->settingsRepo->dns_v6_resp = ui->dnshijack_v6resp->text();
    auto rawRules = rule_editor->toPlainText().split("\n");
    QStringList dnsRules;
    for (const auto& rawRule : rawRules) {
        if (rawRule.trimmed().isEmpty()) continue;
        dnsRules.append(rawRule.trimmed());
    }
    Configs::dataManager->settingsRepo->dns_server_rules = dnsRules;

    Configs::dataManager->settingsRepo->dns_server_listen_lan = ui->dnshijack_allow_lan->isChecked();
    Configs::dataManager->settingsRepo->enable_redirect = ui->redirect_enable->isChecked();
    Configs::dataManager->settingsRepo->redirect_listen_address = ui->redirect_listenaddr->text();
    Configs::dataManager->settingsRepo->redirect_listen_port = ui->redirect_listenport->text().toInt();

    Configs::dataManager->settingsRepo->enable_warp = ui->enable_warp->isChecked();
    Configs::dataManager->settingsRepo->warp_ep = ui->warp_ep->text();
    Configs::dataManager->settingsRepo->warp_ifc_addrs = SplitAndTrim(ui->warp_ifc_addrs->text(), ",", false);
    Configs::dataManager->settingsRepo->warp_private_key = ui->warp_private_key->text();
    Configs::dataManager->settingsRepo->warp_public_key = ui->warp_public_key->text();
    Configs::dataManager->settingsRepo->warp_reserved = SplitAndTrim(ui->warp_reserved->text(), ",", false);

    MW_dialog_message(MwMessage::UpdateSettings, {MwArg::Route});

    QDialog::accept();
}

void DialogManageRoutes::on_new_route_clicked() {
    QMenu menu(this);
    menu.addAction(tr("Structured profile"));
    auto* rawAct = menu.addAction(tr("Raw profile"));
    auto* remoteAct = menu.addAction(tr("Remote profile"));
    auto* chosen = menu.exec(ui->new_route->mapToGlobal(QPoint(0, ui->new_route->height())));
    if (chosen == nullptr) return;

    auto newProfile = Configs::dataManager->routesRepo->NewRouteProfile();
    auto onCreated = [=, this](const std::shared_ptr<Configs::RouteProfile>& chain) {
        chainList << chain;
        reloadProfileItems();
    };
    if (chosen == rawAct) {
        newProfile->isRaw = true;
        auto* rawWidget = new RawRouteItem(this, newProfile);
        rawWidget->setWindowModality(Qt::ApplicationModal);
        rawWidget->show();
        connect(rawWidget, &RawRouteItem::settingsChanged, this, onCreated);
    } else {
        if (chosen == remoteAct) newProfile->isRemote = true;
        routeChainWidget = new RouteItem(this, newProfile);
        routeChainWidget->setWindowModality(Qt::ApplicationModal);
        routeChainWidget->show();
        connect(routeChainWidget, &RouteItem::settingsChanged, this, onCreated);
    }
}

void DialogManageRoutes::on_export_route_clicked()
{
    auto idx = ui->route_profiles->currentRow();
    if (idx < 0) return;

    QApplication::clipboard()->setText(chainList[idx]->ToShareLink());

    QToolTip::showText(QCursor::pos(), tr("Copied!"), this);
    int r = ++tooltipID;
    QTimer::singleShot(1500, [=,this] {
        if (tooltipID != r) return;
        QToolTip::hideText();
    });
}

void DialogManageRoutes::applyImportedProfile(const std::shared_ptr<Configs::RouteProfile>& profile, bool wasOldArray)
{
    if (wasOldArray) {
        auto shell = Configs::dataManager->routesRepo->NewRouteProfile();
        shell->Rules = profile->Rules;
        routeChainWidget = new RouteItem(this, shell);
        routeChainWidget->setWindowModality(Qt::ApplicationModal);
        routeChainWidget->show();
        connect(routeChainWidget, &RouteItem::settingsChanged, this, [=, this](const std::shared_ptr<Configs::RouteProfile>& chain) {
            chainList << chain;
            reloadProfileItems();
        });
    } else {
        chainList << profile;
        currentRoute = profile;
        reloadProfileItems();
    }
}

bool DialogManageRoutes::tryImportRemoteRoutesLink(const QString& text)
{
    bool wasRemoteRouteLink = false;
    QString error;
    auto profiles = Configs::RouteProfile::FromRemoteRoutesLink(text, &wasRemoteRouteLink, &error);
    if (!wasRemoteRouteLink) return false;

    if (profiles.isEmpty()) {
        MessageBoxWarning(tr("Add remote routing profiles"),
                          error.isEmpty() ? tr("No valid remote routing profiles in the link.") : error);
        return true;
    }

    QString prompt = tr("Add these remote routing profiles?") + "\n";
    for (int i = 0; i < profiles.size(); ++i) {
        prompt += QString("\n%1. %2  (%3: %4)")
                      .arg(i + 1)
                      .arg(profiles[i]->remoteURL, tr("auto update"), profiles[i]->autoUpdate ? tr("On") : tr("Off"));
    }
    if (QMessageBox::question(this, tr("Add remote routing profiles"), prompt) != QMessageBox::StandardButton::Yes) {
        return true;
    }

    for (const auto& p : profiles) chainList << p;
    reloadProfileItems();
    updateRemoteProfiles(profiles);
    return true;
}

void DialogManageRoutes::on_import_route_clicked()
{
    const QString clip = QApplication::clipboard()->text().trimmed();
    if (tryImportRemoteRoutesLink(clip)) return;
    if (!clip.isEmpty()) {
        QString fatal, warnings;
        bool wasOldArray = false;
        if (auto profile = Configs::RouteProfile::FromShareInput(clip, &fatal, &warnings, &wasOldArray)) {
            const QString what = wasOldArray ? tr("a routing rule list")
                                             : tr("routing profile \"%1\"").arg(profile->name);
            if (QMessageBox::question(this, tr("Import from clipboard"),
                                      tr("Import %1 from the clipboard?").arg(what))
                == QMessageBox::StandardButton::Yes) {
                if (!warnings.isEmpty()) MessageBoxInfo(tr("Imported with warnings"), warnings);
                applyImportedProfile(profile, wasOldArray);
                return;
            }
        }
    }

    auto w = new QDialog(this);
    w->setWindowTitle(tr("Import routing profile"));
    w->setWindowModality(Qt::ApplicationModal);

    auto layout = new QGridLayout(w);
    auto tEdit = new QTextEdit(w);
    tEdit->setPlaceholderText(tr("Paste a Lumvane route link, a remoteRoute link, a base64 blob, or a JSON rule array"));
    layout->addWidget(tEdit, 0, 0);

    auto buttons = new QDialogButtonBox(QDialogButtonBox::Ok | QDialogButtonBox::Cancel, Qt::Horizontal, w);
    layout->addWidget(buttons, 1, 0);

    connect(buttons, &QDialogButtonBox::accepted, w, [=, this] {
        if (tryImportRemoteRoutesLink(tEdit->toPlainText())) { w->accept(); return; }
        QString fatal, warnings;
        bool wasOldArray = false;
        auto profile = Configs::RouteProfile::FromShareInput(tEdit->toPlainText(), &fatal, &warnings, &wasOldArray);
        if (!profile) {
            MessageBoxWarning(tr("Invalid input"), tr("Could not import this routing profile:\n") + fatal);
            return;
        }
        if (!warnings.isEmpty()) MessageBoxInfo(tr("Imported with warnings"), warnings);
        applyImportedProfile(profile, wasOldArray);
        w->accept();
    });
    connect(buttons, &QDialogButtonBox::rejected, w, &QDialog::reject);

    w->exec();
    w->deleteLater();
}

void DialogManageRoutes::on_clone_route_clicked() {
    auto idx = ui->route_profiles->currentRow();
    if (idx < 0) return;

    auto chainCopy = std::make_shared<Configs::RouteProfile>(*chainList[idx]);
    chainCopy->name = chainCopy->name + " clone";
    chainCopy->id = -1;
    chainList.append(chainCopy);
    reloadProfileItems();
}

void DialogManageRoutes::on_edit_route_clicked() {
    auto idx = ui->route_profiles->currentRow();
    if (idx < 0) return;

    auto onEdited = [=, this](const std::shared_ptr<Configs::RouteProfile>& chain) {
        if (currentRoute == chainList[idx]) currentRoute = chain;
        chainList[idx] = chain;
        reloadProfileItems();
    };

    if (chainList[idx]->isRaw) {
        auto* rawWidget = new RawRouteItem(this, chainList[idx]);
        rawWidget->setWindowModality(Qt::ApplicationModal);
        rawWidget->show();
        connect(rawWidget, &RawRouteItem::settingsChanged, this, onEdited);
    } else {
        routeChainWidget = new RouteItem(this, chainList[idx]);
        routeChainWidget->setWindowModality(Qt::ApplicationModal);
        routeChainWidget->show();
        connect(routeChainWidget, &RouteItem::settingsChanged, this, onEdited);
    }
}

void DialogManageRoutes::on_delete_route_clicked() {
    auto idx = ui->route_profiles->currentRow();
    if (idx < 0) return;
    if (chainList.size() == 1) {
        MessageBoxWarning(tr("Invalid operation"), tr("Routing Profiles cannot be empty, try adding another profile or editing this one"));
        return;
    }

    auto profileToDel = chainList[idx];
    chainList.removeAt(idx);
    if (profileToDel == currentRoute) {
        currentRoute = chainList[0];
    }
    reloadProfileItems();
}

void DialogManageRoutes::on_update_route_clicked() {
    if (routeUpdateRunning) {
        QMenu menu(this);
        auto* cancelAct = menu.addAction(tr("Cancel"));
        auto* chosen = menu.exec(ui->update_route->mapToGlobal(QPoint(0, ui->update_route->height())));
        if (chosen == cancelAct && routeUpdateRunning) {
            routeUpdateCancel = true;
            ui->update_route->setText(tr("Cancelling..."));
        }
        return;
    }

    const int idx = ui->route_profiles->currentRow();
    const bool selIsRemote = idx >= 0 && chainList[idx]->isRemote;

    QMenu menu(this);
    QAction* updateSelAct = selIsRemote ? menu.addAction(tr("Update selected")) : nullptr;
    auto* updateAllAct = menu.addAction(tr("Update all"));
    auto* chosen = menu.exec(ui->update_route->mapToGlobal(QPoint(0, ui->update_route->height())));
    if (chosen == nullptr) return;

    if (chosen == updateSelAct) {
        updateRemoteProfiles({chainList[idx]});
        return;
    }

    if (chosen == updateAllAct) {
        QList<std::shared_ptr<Configs::RouteProfile>> remotes;
        for (const auto& p : chainList) {
            if (p->isRemote && !p->remoteURL.trimmed().isEmpty()) remotes << p;
        }
        if (remotes.isEmpty()) {
            MessageBoxInfo(tr("No remote profiles"), tr("There are no remote routing profiles to update."));
            return;
        }
        updateRemoteProfiles(remotes);
    }
}

void DialogManageRoutes::updateRemoteProfiles(const QList<std::shared_ptr<Configs::RouteProfile>>& profiles) {
    if (routeUpdateRunning || profiles.isEmpty()) return;
    routeUpdateRunning = true;
    routeUpdateCancel = false;
    const int total = profiles.size();

    auto progressText = [total](int current) {
        return total <= 1 ? tr("Updating...") : tr("Updating (%1 / %2)").arg(current).arg(total);
    };
    ui->update_route->setText(progressText(1));

    runOnNewThread([=, this] {
        QStringList failures;
        int ok = 0;
        for (int i = 0; i < profiles.size(); ++i) {
            if (routeUpdateCancel.load()) break;
            const int current = i + 1;
            runOnUiThread([=, this] {
                if (routeUpdateRunning && !routeUpdateCancel.load())
                    ui->update_route->setText(progressText(current));
            });
            QString warnings;
            const QString err = RouteUpdate::UpdateProfile(profiles[i], &warnings);
            if (err.isEmpty()) ok++;
            else failures << (profiles[i]->name + ": " + err);
        }
        const bool cancelled = routeUpdateCancel.load();
        runOnUiThread([=, this] {
            routeUpdateRunning = false;
            ui->update_route->setText(tr("Update"));
            reloadProfileItems();
            if (cancelled) {
                MessageBoxInfo(tr("Update cancelled"),
                               tr("Cancelled: updated %1 of %2, %3 failed.").arg(ok).arg(total).arg(failures.size()));
            } else if (failures.isEmpty()) {
                MessageBoxInfo(tr("Update complete"), tr("Updated %1 remote routing profile(s).").arg(ok));
            } else {
                MessageBoxWarning(tr("Update finished with errors"),
                                  tr("Updated %1, failed %2:\n%3").arg(ok).arg(failures.size()).arg(failures.join("\n")));
            }
        });
    });
}
