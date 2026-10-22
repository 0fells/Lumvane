#include "include/ui/mainwindow.h"

#include <QApplication>
#include <QHeaderView>
#include <QScrollBar>
#include <QTimer>
#include <QToolButton>

#include "include/api/RPC.h"
#include "include/database/GroupsRepo.h"
#include "include/database/RoutesRepo.h"
#include "include/stats/autoselector/AutoSelectorMonitor.hpp"
#include "include/ui/setting/Icon.hpp"
#include "include/ui/stats/dialog_auto_selector.h"
#include "include/ui/utils/ProfilesTableFilterHeader.h"
#include "include/ui/utils/ProfilesTableModel.h"
#include "include/ui/widget/StartStopButton.hpp"
#include "include/ui/widget/Sparkline.hpp"
#include "include/ui/widget/EmptyProfilesView.hpp"
#include "include/ui/setting/ThemeManager.hpp"
#include "include/ui/widget/TitleBar.hpp"
#include "include/ui/mainWindow/MainWindowInternal.h"

#include <QLabel>
#include <QLineEdit>

void MainWindow::applyTopBarMetrics() {
    const QSize contentMin = minimumSizeHint();
    setMinimumSize(qMax(designMinimumSize.width(), contentMin.width()),
                   qMax(designMinimumSize.height(), contentMin.height()));
}

void MainWindow::applyMainWindowChrome() {
    const auto tk = ThemeManager::Tokens();
    const QPalette pal = qApp->palette();
    const QColor text = pal.color(QPalette::WindowText);

    const QList<QPair<QToolButton*, QString>> menuButtons = {
        {ui->toolButton_program, "program"}, {ui->toolButton_preferences, "settings"},
        {ui->toolButton_testing, "groups"}, {ui->toolButton_routing, "routing"},
        {ui->toolButton_tools, "tools"},
    };
    for (const auto &[button, icon] : menuButtons) {
        button->setIcon(Icon::Themed(icon, tk.muted));
        button->setIconSize({18, 18});
    }
    if (profileSearch != nullptr) {
        if (auto *icon = profileSearch->findChild<QAction *>("searchIcon")) icon->setIcon(Icon::Themed("search", tk.muted, 14));
        if (auto *clear = profileSearch->findChild<QAction *>("_q_qlineeditclearaction")) clear->setIcon(Icon::Themed("clear", tk.muted, 12));
    }
    ui->stats_widget->setMinimumHeight(ui->stats_widget->tabBar()->sizeHint().height());
    panelToggleCollapsed = -1;
    updatePanelToggle();
    if (titleBar != nullptr) {
        titleBar->setCustomFrame(true);
        titleBar->applyTheme();
    }

    const QColor window = pal.color(QPalette::Window);
    const QColor sunken = QColor::fromRgbF((window.redF() + tk.surface.redF()) / 2, (window.greenF() + tk.surface.greenF()) / 2,
                                           (window.blueF() + tk.surface.blueF()) / 2);
    ui->down_tab->setAttribute(Qt::WA_StyledBackground);
    ui->stats_widget->setAutoFillBackground(false);

    ui->centralwidget->setStyleSheet(QString(R"(
QFrame#topBar, QFrame#connectionPanel, QFrame#statusFrame { background: %1; border: 0; }
QFrame#topBar, QFrame#connectionPanel { border-bottom: 1px solid %2; }
QFrame#statusFrame { border-top: 1px solid %2; }
QTabWidget#tabWidget::tab-bar, QTabWidget#stats_widget::tab-bar { left: 10px; }
QTabWidget#tabWidget > QTabBar::tab { padding: 10px 12px; }
QTableView#profilesTableView { border: 0; border-radius: 0; }
QSplitter#splitter::handle { background: %2; }
QWidget#down_tab { background: %8; }
QTabWidget#stats_widget::pane { border: 0; }
QTextBrowser#masterLogBrowser, QTableWidget#connections { border: 0; border-radius: 0; background: %8; }
QToolButton#panelToggle { border: 0; border-radius: 4px; padding: 4px; margin-right: 6px; }
QToolButton#panelToggle:hover { background: %3; }
QLabel#groupTabCount { color: %4; padding-left: 2px; }
QLabel#noMatchesLabel { color: %4; }
QFrame#modeSegment { background: %3; border-radius: 8px; }
QFrame#modeSegment QToolButton { padding: 5px 12px; border: 0; border-radius: 6px; background: transparent; color: %4; }
QFrame#modeSegment QToolButton:hover { color: %5; background: %2; }
QFrame#modeSegment QToolButton:checked { background: %6; color: %7; }
QFrame#modeSegment QToolButton:checked:hover { background: %6; }
QFrame#speedSeparator { color: %2; }
QLabel#label_running, QLabel#label_inbound, QLabel#label_route, QLabel#label_versions { color: %4; }
QToolButton#toolButton_program, QToolButton#toolButton_preferences, QToolButton#toolButton_testing,
QToolButton#toolButton_routing, QToolButton#toolButton_tools {
    padding: 5px 10px 5px 8px; border: 0; border-radius: 6px; background: transparent;
}
QToolButton#toolButton_program:hover, QToolButton#toolButton_preferences:hover, QToolButton#toolButton_testing:hover,
QToolButton#toolButton_routing:hover, QToolButton#toolButton_tools:hover { background: %3; }
QToolButton#toolButton_program::menu-indicator, QToolButton#toolButton_preferences::menu-indicator,
QToolButton#toolButton_testing::menu-indicator, QToolButton#toolButton_routing::menu-indicator,
QToolButton#toolButton_tools::menu-indicator {
    image: url(:/icon/ui/qss-chevron-down.svg); width: 9px; height: 9px;
    subcontrol-origin: padding; subcontrol-position: center right; right: 4px;
}
)").arg(tk.surface.name(), tk.border.name(), tk.subtle.name(), tk.muted.name(), text.name())
     .arg(tk.accentSoft.name(), tk.accent.name(), sunken.name()));

    if (speedSparkline != nullptr) speedSparkline->setColor(tk.accent);
    if (emptyProfilesView != nullptr) emptyProfilesView->applyTheme();
    if (profileSearch != nullptr && ui->tabWidget->cornerWidget() != nullptr) {
        const int spare = ui->tabWidget->tabBar()->sizeHint().height() - profileSearch->sizeHint().height();
        const int margin = qMax(0, spare / 2);
        ui->tabWidget->cornerWidget()->layout()->setContentsMargins(0, margin, 10, qMax(0, spare - margin));
    }
    applyLogBrowserFont();
    refreshSpeedLabel();
    refresh_startstop_button();
}

QString MainWindow::formatRate(double bytesPerSec) const {
    static const char *units[] = {QT_TR_NOOP("B/s"), QT_TR_NOOP("KB/s"), QT_TR_NOOP("MB/s"), QT_TR_NOOP("GB/s")};
    int u = 0;
    double v = qMax(0.0, bytesPerSec);
    while (v >= 1000 && u < 3) { v /= 1024; ++u; }
    const QLocale loc;
    const QString num = (u == 0 || v >= 10) ? loc.toString(qRound(v)) : loc.toString(v, 'f', 1);
    return num + QChar(0x00A0) + tr(units[u]);
}

QString MainWindow::windowTitleText() const {
    const auto *settings = Configs::dataManager->settingsRepo.get();
    QString title = software_name;
    if (running != nullptr) {
        QString mode;
        if (settings->spmode_vpn && settings->spmode_system_proxy) mode = "Tun + " + tr("System Proxy");
        else if (settings->spmode_vpn) mode = "Tun";
        else if (settings->spmode_system_proxy) mode = tr("System Proxy");
        if (!mode.isEmpty()) title += QString::fromUtf8(" — ") + mode;
    }
    if (select_mode) title += " [" + tr("Select") + "]";
    if (!title_error.isEmpty()) title += " [" + title_error + "]";
    return title;
}

void MainWindow::refreshSpeedLabel() {
    const auto *settings = Configs::dataManager->settingsRepo.get();
    const bool hide = settings->disable_traffic_stats;
    ui->label_speed->setVisible(!hide);
    ui->speedSeparator->setVisible(!hide);
    if (speedSparkline != nullptr) speedSparkline->setVisible(!hide);
    if (hide) return;

    const auto tk = ThemeManager::Tokens();
    const bool live = running != nullptr && !traffic_update_cache.isEmpty();
    const QString down = live ? formatRate(lastDownRate) : QString("—");
    const QString up = live ? formatRate(lastUpRate) : QString("—");
    const qreal base = font().pointSizeF() > 0 ? font().pointSizeF() : 9.0;
    const qreal big = base * 1.75;
    ui->label_speed->setText(QString(
        "<table cellspacing='0' cellpadding='0'><tr>"
        "<td style='color:%1'>↓ %2</td><td width='24'></td><td style='color:%1'>↑ %3</td></tr><tr>"
        "<td style='font-size:%4pt; font-weight:600'>%5</td><td></td>"
        "<td style='font-size:%4pt; font-weight:600'>%6</td></tr></table>")
        .arg(tk.muted.name(), tr("Download"), tr("Upload"), QString::number(big, 'f', 1), down, up));
    ui->label_speed->setToolTip(traffic_update_cache);
}

void MainWindow::setRunningSubtitle(const QString &text) {
    runningSubtitle = text;
    const int w = ui->label_running->width();
    ui->label_running->setText(w > 0 ? ui->label_running->fontMetrics().elidedText(text, Qt::ElideRight, w) : text);
    ui->label_running->setToolTip(ui->label_running->text() == text ? QString() : text);
}

bool MainWindow::bottomPanelCollapsed() const {
    const auto sizes = ui->splitter->sizes();
    return sizes.size() == 2 && sizes[1] <= ui->stats_widget->minimumHeight() + 2;
}

void MainWindow::toggleBottomPanel() {
    const auto sizes = ui->splitter->sizes();
    if (sizes.size() != 2) return;
    const int total = sizes[0] + sizes[1];
    const int collapsed = ui->stats_widget->minimumHeight();
    if (bottomPanelCollapsed()) {
        int h = expandedPanelHeight > collapsed ? expandedPanelHeight : total / 4;
        h = qMin(h, total - ui->tabWidget->minimumSizeHint().height());
        ui->splitter->setSizes({total - h, h});
    } else {
        expandedPanelHeight = sizes[1];
        ui->splitter->setSizes({total - collapsed, collapsed});
    }
    updatePanelToggle();
}

void MainWindow::updatePanelToggle() {
    if (panelToggle == nullptr) return;
    const bool collapsed = bottomPanelCollapsed();
    if (panelToggleCollapsed == int(collapsed)) return;
    panelToggleCollapsed = int(collapsed);
    panelToggle->setIcon(Icon::Themed(collapsed ? "chevron-up" : "chevron-down", ThemeManager::Tokens().muted, 14));
    panelToggle->setToolTip(collapsed ? tr("Expand panel") : tr("Collapse panel"));
    panelToggle->setAccessibleName(panelToggle->toolTip());
}

void MainWindow::setGroupTabCount(int index, int count) {
    auto *bar = ui->tabWidget->tabBar();
    if (index < 0 || index >= bar->count()) return;
    auto *label = qobject_cast<QLabel *>(bar->tabButton(index, QTabBar::RightSide));
    if (label == nullptr) {
        label = new QLabel(bar);
        label->setObjectName("groupTabCount");
        bar->setTabButton(index, QTabBar::RightSide, label);
    }
    const QString text = QString::number(count);
    if (label->text() != text) {
        label->setText(text);
        label->adjustSize();
    }
}

void MainWindow::updateEmptyProfilesView() {
    if (emptyProfilesView == nullptr || profilesTableModel == nullptr) return;
    const bool empty = profilesTableModel->rowCount() == 0;
    ui->profilesTableView->horizontalHeader()->setVisible(!empty);
    emptyProfilesView->setGeometry(ui->profilesTableView->viewport()->rect());
    emptyProfilesView->setVisible(empty);
    if (empty) emptyProfilesView->raise();
    if (noMatchesLabel != nullptr) {
        const bool noMatches = !empty && profilesFilterModel != nullptr && profilesFilterModel->rowCount() == 0;
        noMatchesLabel->setGeometry(ui->profilesTableView->viewport()->rect().adjusted(16, 48, -16, 0));
        noMatchesLabel->setVisible(noMatches);
    }
}

void MainWindow::UpdateDataView(bool force)
{
    const auto now = QDateTime::currentMSecsSinceEpoch();
    if (!force && now - lastUpdatedMs.load() < 100)
    {
        return;
    }
    auto html = dataViewHtmlGenerator_.buildHtml();
    runOnUiThread([=, this] {
        ui->data_view->setHtml(html);
    }, true);
    lastUpdatedMs.store(QDateTime::currentMSecsSinceEpoch());
}

void MainWindow::setDownloadReport(const DownloadProgressReport& report, bool show)
{
    dataViewHtmlGenerator_.setDownloadReport(report, show);
}

void MainWindow::refresh_auto_selector_view()
{
    const auto view = Stats::autoSelectorMonitor->Snapshot();
    dataViewHtmlGenerator_.setAutoSelectorStatus(view.valid ? view.summary() : QString(),
                                                 view.valid ? view.detail() : QString());
    ui->actionAuto_Selector->setVisible(view.valid);
    UpdateDataView();
    if (m_autoSelectorDialog != nullptr) m_autoSelectorDialog->refresh();
}

void MainWindow::updateLogFilterFields() {
    QMutexLocker locker(&logMutex);
    includeKeywords.clear();
    excludeKeywords.clear();
    for (const auto& inKeyword : Configs::dataManager->settingsRepo->log_include_keyword) includeKeywords.append(inKeyword);
    for (const auto& exKeyword : Configs::dataManager->settingsRepo->log_exclude_keyword) excludeKeywords.append(exKeyword);
    includeCombined.setPattern(Configs::dataManager->settingsRepo->log_include_regex.join("|"));
    excludeCombined.setPattern(Configs::dataManager->settingsRepo->log_exclude_regex.join("|"));
    includeCombined.optimize();
    excludeCombined.optimize();
}

void MainWindow::applyProfileFilters()
{
    if (!profilesFilterModel) return;
    profilesFilterModel->setFilters(typeFilterString, addressFilterString, nameFilterString, countryFilterString);
    refresh_proxy_list_column_size();
}

void MainWindow::refresh_status(const QString &traffic_update) {
    const auto* settings = Configs::dataManager->settingsRepo.get();

    if (!traffic_update.isEmpty() && !settings->disable_traffic_stats) {
        traffic_update_cache = traffic_update;
        if (traffic_update == "STOP") {
            traffic_update_cache = "";
            lastDownRate = lastUpRate = 0;
            if (speedSparkline) speedSparkline->clear();
        } else {
            refreshSpeedLabel();
            return;
        }
    }

    refreshSpeedLabel();

    QString group_name;
    if (running != nullptr) {
        auto group = Configs::dataManager->groupsRepo->GetGroup(running->gid);
        if (group != nullptr) group_name = group->name;
    }

    if (QDateTime::currentSecsSinceEpoch() - last_test_time > 2) {
        QString sub;
        if (running) {
            QStringList parts{running->outbound->DisplayName()};
            if (!group_name.isEmpty()) parts << group_name;
            if (!running->runningCountryInfo.isEmpty()) parts << running->runningCountryInfo;
            sub = parts.join(" · ");
        } else if (const int startId = get_profile_to_start(); startId >= 0) {
            const auto next = Configs::dataManager->profilesRepo->GetProfile(startId);
            if (next && next->outbound) {
                QStringList parts{next->outbound->DisplayName()};
                if (const auto group = Configs::dataManager->groupsRepo->GetGroup(next->gid); group && !group->name.isEmpty()) parts << group->name;
                sub = parts.join(" · ");
            }
        } else {
            sub = tr("No profiles yet");
        }
        setRunningSubtitle(sub);
    }
    const auto display_socks = DisplayAddress(settings->inbound_address, settings->inbound_socks_port);
    const auto inbound_disabled = settings->disable_mixed_inbound;
    const auto inbound_txt = QString("Mixed: %1").arg(inbound_disabled ? "Disabled" : display_socks);
    ui->label_inbound->setText(inbound_txt);
    ui->label_versions->setText(CoreVersionText);
    ui->checkBox_VPN->setChecked(settings->spmode_vpn);
    ui->checkBox_SystemProxy->setChecked(settings->spmode_system_proxy);
    if (select_mode) {
        setRunningSubtitle(tr("Select mode, double-click or press Enter to select a profile, press ESC to exit."));
    }

    const auto route = Configs::dataManager->routesRepo->GetRouteProfile(settings->current_route_id);
    const QString activeRouteName = (route && route->name != "Default") ? route->name : "";
    ui->label_route->setText(tr("Routing: %1").arg(route ? route->name : QString("Default")));

    auto make_title = [=,this](bool isTray) {
        QStringList tt;
        if (!isTray && Configs::IsAdmin()) tt << "[Admin]";
        if (select_mode) tt << "[" + tr("Select") + "]";
        if (!title_error.isEmpty()) tt << "[" + title_error + "]";
        if (settings->spmode_vpn && !settings->spmode_system_proxy) tt << "[Tun]";
        if (!settings->spmode_vpn && settings->spmode_system_proxy) tt << "[" + tr("System Proxy") + "]";
        if (settings->spmode_vpn && settings->spmode_system_proxy) tt << "[Tun+" + tr("System Proxy") + "]";
        tt << software_name;
        if (!activeRouteName.isEmpty()) {
            tt << "[" + activeRouteName + "]";
        }
        if (running != nullptr) {
            tt << running->outbound->DisplayTypeAndName() + "@" + group_name;
            if (!running->runningCountryInfo.isEmpty()) {
                tt << running->runningCountryInfo;
            }
        }
        return tt.join(isTray ? "\n" : " ");
    };

    auto icon_status_new = Icon::NONE;

    if (running != nullptr) {
        if (settings->spmode_vpn) {
            icon_status_new = Icon::VPN;
        } else if (settings->system_dns_set && settings->spmode_system_proxy) {
            icon_status_new = Icon::SYSTEM_PROXY_DNS;
        } else if (settings->system_dns_set) {
            icon_status_new = Icon::DNS;
        } else if (settings->spmode_system_proxy) {
            icon_status_new = Icon::SYSTEM_PROXY;
        } else {
            icon_status_new = Icon::RUNNING;
        }
    }

    setWindowTitle(windowTitleText());

    if (tray != nullptr) {
        tray->setToolTip(make_title(true));
        if (icon_status_new != icon_status) tray->setIcon(Icon::GetTrayIcon(icon_status_new));
    }

    icon_status = icon_status_new;

    refresh_startstop_button();
}

void MainWindow::refresh_startstop_button() {
    auto *btn = ui->toolButton_startstop;
    if (btn == nullptr) return;

    const auto &settings = Configs::dataManager->settingsRepo;

    auto mode = StartStopButton::Mode::Off;
    if (running != nullptr) {
        if (settings->spmode_vpn) mode = StartStopButton::Mode::Tun;
        else if (settings->system_dns_set && settings->spmode_system_proxy) mode = StartStopButton::Mode::SystemProxyDns;
        else if (settings->system_dns_set) mode = StartStopButton::Mode::Dns;
        else if (settings->spmode_system_proxy) mode = StartStopButton::Mode::SystemProxy;
        else mode = StartStopButton::Mode::Core;
    }
    btn->setMode(mode);

    StartStopButton::State state;
    if (m_profileConnecting) state = StartStopButton::State::Connecting;
    else if (m_profileDisconnecting) state = StartStopButton::State::Disconnecting;
    else if (running != nullptr) state = StartStopButton::State::Running;
    else if (get_profile_to_start() >= 0) state = StartStopButton::State::Idle;
    else state = StartStopButton::State::Disabled;
    btn->setState(state);

    const auto tk = ThemeManager::Tokens();
    if (running != nullptr) {
        if (runningSinceSecs == 0) runningSinceSecs = QDateTime::currentSecsSinceEpoch();
    } else {
        runningSinceSecs = 0;
    }

    QString stateText;
    QColor dot = tk.muted;
    if (select_mode) {
        stateText = tr("Select") + " *";
    } else if (m_profileConnecting) {
        stateText = tr("Connecting...");
        dot = tk.slow;
    } else if (m_profileDisconnecting) {
        stateText = tr("Disconnecting...");
        dot = tk.slow;
    } else if (running != nullptr) {
        const qint64 mins = (QDateTime::currentSecsSinceEpoch() - runningSinceSecs) / 60;
        const QString since = mins < 1 ? tr("< 1 min")
                            : mins < 60 ? tr("%1 min").arg(mins)
                            : tr("%1 h %2 min").arg(mins / 60).arg(mins % 60);
        stateText = tr("Connected") + QString(" <span style='color:%1; font-weight:400'>· %2</span>").arg(tk.muted.name(), since.toHtmlEscaped());
        dot = tk.good;
    } else {
        stateText = tr("Not connected");
    }
    ui->label_status->setText(QString("<span style='color:%1'>●</span>&nbsp;%2").arg(dot.name(), stateText));

    if (trayStartStop != nullptr) {
        switch (state) {
            case StartStopButton::State::Connecting: trayStartStop->setText(tr("Connecting...")); break;
            case StartStopButton::State::Disconnecting: trayStartStop->setText(tr("Disconnecting...")); break;
            case StartStopButton::State::Running: trayStartStop->setText(tr("Disconnect")); break;
            default: trayStartStop->setText(tr("Connect")); break;
        }
        trayStartStop->setEnabled(state == StartStopButton::State::Running || state == StartStopButton::State::Idle);
    }
}

void MainWindow::update_traffic_graph(int proxyDl, int proxyUp, int directDl, int directUp)
{
    if (running == nullptr && lastDownRate == 0 && lastUpRate == 0 && proxyDl + proxyUp + directDl + directUp == 0) return;
    lastDownRate = static_cast<double>(proxyDl) + directDl;
    lastUpRate = static_cast<double>(proxyUp) + directUp;
    if (speedSparkline) speedSparkline->push(lastDownRate + lastUpRate);
    refreshSpeedLabel();

    if (speedChartWidget) {
        QMap<SpeedWidget::GraphType, long> pointData;
        pointData[SpeedWidget::OUTBOUND_PROXY_UP] = proxyUp;
        pointData[SpeedWidget::OUTBOUND_PROXY_DOWN] = proxyDl;
        pointData[SpeedWidget::OUTBOUND_DIRECT_UP] = directUp;
        pointData[SpeedWidget::OUTBOUND_DIRECT_DOWN] = directDl;

        speedChartWidget->AddPointData(pointData);
    }
}

void MainWindow::refresh_proxy_list_column_size() {
    const auto group = Configs::dataManager->groupsRepo->CurrentGroup();
    if (!group || !ui->profilesTableView->isVisible()) return;

    auto *hHeader = dynamic_cast<ProfilesTableFilterHeader*>(ui->profilesTableView->horizontalHeader());
    QTimer::singleShot(0, ui->profilesTableView, [=, this]() {
        if (m_adjustingColumns) return;
        m_adjustingColumns = true;
        QScrollBar *vBar = ui->profilesTableView->verticalScrollBar();
        const bool vBarBlocked = vBar->blockSignals(true);
        hHeader->blockSignals(true);
        constexpr int columnCount = ProfilesTableModel::ColumnCount;
        if (!group->column_width.isEmpty() && group->column_width.size() != columnCount) {
            group->column_width.clear();
        }
        if (group->column_width.isEmpty()) {
            hHeader->setSectionResizeMode(ProfilesTableModel::ColType, QHeaderView::ResizeToContents);
            hHeader->setSectionResizeMode(ProfilesTableModel::ColAddress, QHeaderView::ResizeToContents);
            hHeader->setSectionResizeMode(ProfilesTableModel::ColName, QHeaderView::Stretch);
            hHeader->setSectionResizeMode(ProfilesTableModel::ColTestResult, QHeaderView::ResizeToContents);
            hHeader->setSectionResizeMode(ProfilesTableModel::ColTraffic, QHeaderView::ResizeToContents);
            for (int col : {ProfilesTableModel::ColType, ProfilesTableModel::ColAddress,
                            ProfilesTableModel::ColTestResult, ProfilesTableModel::ColTraffic}) {
                if (group->calculated_column_width.size() > col &&
                    group->calculated_column_width[col] > hHeader->sectionSize(col)) {
                    hHeader->setSectionResizeMode(col, QHeaderView::Fixed);
                    hHeader->resizeSection(col, group->calculated_column_width[col]);
                }
            }
            const int addressCap = qMax(180, ui->profilesTableView->viewport()->width() / 3);
            if (hHeader->sectionSize(ProfilesTableModel::ColAddress) > addressCap) {
                hHeader->setSectionResizeMode(ProfilesTableModel::ColAddress, QHeaderView::Fixed);
                hHeader->resizeSection(ProfilesTableModel::ColAddress, addressCap);
            }
            const int textWidth = ui->profilesTableView->viewport()->width() - hHeader->sectionSize(ProfilesTableModel::ColType) -
                                  hHeader->sectionSize(ProfilesTableModel::ColTestResult) - hHeader->sectionSize(ProfilesTableModel::ColTraffic);
            const int addressShare = textWidth * 35 / 100;
            const int addressContent = hHeader->sectionSize(ProfilesTableModel::ColAddress);
            if (hHeader->sectionSize(ProfilesTableModel::ColAddress) < addressShare) {
                hHeader->setSectionResizeMode(ProfilesTableModel::ColAddress, QHeaderView::Fixed);
                hHeader->resizeSection(ProfilesTableModel::ColAddress, addressShare);
            }
            ui->profilesTableView->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
            group->clearCalculatedColumnWidth();
            for (int i = 0; i < columnCount; i++) {
                auto size = hHeader->sectionSize(i);
                hHeader->setSectionResizeMode(i, QHeaderView::Interactive);
                hHeader->resizeSection(i, size);
                group->calculated_column_width << (i == ProfilesTableModel::ColAddress ? addressContent : size);
            }
        } else {
            group->clearCalculatedColumnWidth();
            for (int i = 0; i < columnCount; i++) {
                hHeader->setSectionResizeMode(i, QHeaderView::Interactive);
                hHeader->resizeSection(i, group->column_width.at(i));
            }
            ui->profilesTableView->setHorizontalScrollBarPolicy(Qt::ScrollBarAsNeeded);
        }
        hHeader->adjustPositions();
        hHeader->blockSignals(false);
        ui->profilesTableView->viewport()->update();
        vBar->blockSignals(vBarBlocked);
        m_adjustingColumns = false;
    });
}

void MainWindow::refresh_proxy_list(const QList<int>& ids, bool mayNeedReset, RefreshAnchor anchor) {
    if (!Configs::dataManager->settingsRepo->refreshing_group) saveProfileFocusState();
    refresh_proxy_list_impl(ids, mayNeedReset);
    if (mayNeedReset) restoreProfileFocusState(anchor);
}

void MainWindow::refresh_proxy_list_impl(const QList<int>& ids, bool mayNeedReset) {
    const auto currentGroup = Configs::dataManager->groupsRepo->CurrentGroup();
    if (currentGroup == nullptr)
    {
        MW_show_log("Could not find current group!");
        return;
    }
    refresh_proxy_list_impl_refresh_data(ids, mayNeedReset);
    refresh_proxy_list_column_size();
}

void MainWindow::refresh_proxy_list_impl_refresh_data(const QList<int>& ids, bool mayNeedReset) {
    const auto currentGroup = Configs::dataManager->groupsRepo->CurrentGroup();
    if (currentGroup == nullptr) return;
    if (!ids.isEmpty()) {
        for (auto id:ids) profilesTableModel->refreshProfileId(id);
    } else {
        profilesTableModel->refreshTable(currentGroup->profiles, mayNeedReset);
    }
    const auto tabOrder = Configs::dataManager->groupsRepo->GetGroupsTabOrder();
    for (int i = 0; i < tabOrder.size(); i++) {
        if (const auto group = Configs::dataManager->groupsRepo->GetGroup(tabOrder[i])) setGroupTabCount(i, group->profiles.size());
    }
}

void MainWindow::url_test_current() {
    last_test_time = QDateTime::currentSecsSinceEpoch();
    setRunningSubtitle(tr("Testing"));

    runOnNewThread([=,this] {
        libcore::TestReq req;
        req.test_current = true;
        req.url = Configs::dataManager->settingsRepo->test_latency_url.toStdString();

        bool rpcOK;
        auto result = API::defaultClient->Test(&rpcOK, req);
        if (!rpcOK || result.results.empty()) return;

        auto latency = result.results[0].latency_ms.value();
        last_test_time = QDateTime::currentSecsSinceEpoch();

        runOnUiThread([=,this] {
            if (!result.results[0].error.value().empty()) {
                MW_show_log(QString("UrlTest error: %1").arg(QString::fromStdString(result.results[0].error.value())));
            }
            if (latency <= 0) {
                setRunningSubtitle(tr("Test Result") + ": " + tr("Unavailable"));
            } else if (latency > 0) {
                setRunningSubtitle(tr("Test Result") + ": " + QString("%1 ms").arg(latency));
            }
        });
    });
}
