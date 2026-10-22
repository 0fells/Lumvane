#include "include/ui/widget/TrayProfileSelector.hpp"

#include <QVBoxLayout>
#include <QHBoxLayout>
#include <QLabel>
#include <QLineEdit>
#include <QListWidget>
#include <QListWidgetItem>
#include <QApplication>
#include <QPainter>
#include <QPushButton>
#include <QStyledItemDelegate>
#include <QToolButton>
#include <QTimer>
#include <QKeyEvent>
#include <QScreen>
#include <QGuiApplication>
#include <QPalette>

#include "include/global/Configs.hpp"
#include "include/global/Common.h"
#include "include/database/GroupsRepo.h"
#include "include/database/ProfilesRepo.h"
#include "include/database/RoutesRepo.h"
#include "include/database/SettingsRepo.h"
#include "include/ui/setting/Icon.hpp"
#include "include/ui/setting/ThemeManager.hpp"
#include "include/ui/utils/ProfileTypeDelegate.h"

namespace {
    constexpr int kPerPage = 10;
    constexpr int kRowHeight = 30;
    constexpr int kSearchDebounceMs = 200;

    enum ItemKind { KindGroup = 1, KindProfile, KindRoute };
    constexpr int RoleKind = Qt::UserRole;
    constexpr int RoleId = Qt::UserRole + 1;
    constexpr int RoleCurrent = Qt::UserRole + 2;

    class SelectorItemDelegate : public QStyledItemDelegate {
    public:
        using QStyledItemDelegate::QStyledItemDelegate;

        void paint(QPainter *painter, const QStyleOptionViewItem &option, const QModelIndex &index) const override {
            QStyleOptionViewItem opt = option;
            initStyleOption(&opt, index);
            const QString text = opt.text;
            opt.text.clear();
            opt.state &= ~QStyle::State_HasFocus;
            const QWidget *widget = opt.widget;
            QStyle *style = widget ? widget->style() : QApplication::style();
            style->drawControl(QStyle::CE_ItemViewItem, &opt, painter, widget);

            const auto tk = ThemeManager::Tokens();
            const QRect r = opt.rect;
            const bool enabled = index.flags() & Qt::ItemIsEnabled;
            const bool group = index.data(RoleKind).toInt() == KindGroup;
            constexpr int kPad = ProfileTextDelegate::TextPadding, kChevron = 5;

            painter->save();
            painter->setRenderHint(QPainter::Antialiasing);
            if (index.data(RoleCurrent).toBool())
                painter->fillRect(QRect(r.left(), r.top() + 4, 3, r.height() - 8), tk.accent);

            QRect textRect = r.adjusted(kPad + 4, 0, -kPad, 0);
            if (group) {
                const qreal cx = r.right() - kPad - kChevron, cy = r.center().y() + 0.5;
                painter->setPen(QPen(tk.muted, 1.5, Qt::SolidLine, Qt::RoundCap, Qt::RoundJoin));
                painter->drawPolyline(QPolygonF({QPointF(cx, cy - kChevron), QPointF(cx + kChevron, cy), QPointF(cx, cy + kChevron)}));
                textRect.setRight(cx - kPad);
            }
            painter->setFont(opt.font);
            painter->setPen(!enabled ? tk.muted
                            : opt.palette.color(QPalette::Normal, (opt.state & QStyle::State_Selected) ? QPalette::HighlightedText : QPalette::Text));
            painter->drawText(textRect, Qt::AlignLeft | Qt::AlignVCenter | Qt::TextSingleLine,
                              opt.fontMetrics.elidedText(text, Qt::ElideRight, textRect.width()));
            painter->restore();
        }

        QSize sizeHint(const QStyleOptionViewItem &option, const QModelIndex &index) const override {
            return {QStyledItemDelegate::sizeHint(option, index).width(), kRowHeight};
        }
    };

    QToolButton *makeToolButton(QWidget *parent, const QString &tip) {
        auto *b = new QToolButton(parent);
        b->setAutoRaise(true);
        b->setToolTip(tip);
        b->setFixedSize(28, 28);
        return b;
    }
}

TrayProfileSelector::TrayProfileSelector(Mode mode, Callbacks cb, QWidget *parent)
    : QFrame(parent), m_mode(mode), m_cb(std::move(cb)) {
    setWindowFlags(Qt::Tool | Qt::FramelessWindowHint | Qt::WindowStaysOnTopHint);
    setAttribute(Qt::WA_DeleteOnClose);
    setAttribute(Qt::WA_TranslucentBackground);
    setFrameShape(QFrame::NoFrame);
    setMinimumWidth(300);

    const auto tk = ThemeManager::Tokens();

    auto *outer = new QVBoxLayout(this);
    outer->setContentsMargins(0, 0, 0, 0);

    auto *card = new QFrame(this);
    card->setObjectName(QStringLiteral("trayCard"));
    card->setStyleSheet(QStringLiteral(
        "QFrame#trayCard { background-color:%1; border:1px solid %2; border-radius:8px; }")
        .arg(tk.surface.name(), tk.border.name()));
    outer->addWidget(card);

    auto *root = new QVBoxLayout(card);
    root->setContentsMargins(10, 10, 10, 10);
    root->setSpacing(6);

    auto *searchRow = new QHBoxLayout();
    m_search = new QLineEdit(card);
    m_search->setObjectName(QStringLiteral("traySearch"));
    m_search->setPlaceholderText(tr("Search…"));
    m_search->setClearButtonEnabled(true);
    m_search->installEventFilter(this);
    auto *closeBtn = makeToolButton(card, tr("Close"));
    closeBtn->setIcon(Icon::Themed(QStringLiteral("clear"), tk.muted, 16));
    searchRow->addWidget(m_search, 1);
    searchRow->addWidget(closeBtn);
    root->addLayout(searchRow);

    auto *header = new QHBoxLayout();
    m_backBtn = makeToolButton(card, tr("Back to groups"));
    m_backBtn->setArrowType(Qt::LeftArrow);
    m_title = new QLabel(card);
    m_title->setStyleSheet(QStringLiteral("font-weight:600; border:none;"));
    header->addWidget(m_backBtn);
    header->addWidget(m_title, 1);
    root->addLayout(header);

    m_stopBtn = new QPushButton(card);
    root->addWidget(m_stopBtn);

    m_list = new QListWidget(card);
    m_list->setUniformItemSizes(true);
    m_list->setHorizontalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    m_list->setVerticalScrollBarPolicy(Qt::ScrollBarAlwaysOff);
    m_list->ensurePolished();
    m_list->setFixedHeight(kPerPage * kRowHeight + 2 * m_list->frameWidth());
    m_list->installEventFilter(this);
    m_list->setItemDelegate(new SelectorItemDelegate(m_list));
    root->addWidget(m_list, 1);

    auto *footer = new QHBoxLayout();
    m_prevBtn = makeToolButton(card, tr("Previous page"));
    m_prevBtn->setArrowType(Qt::LeftArrow);
    m_pageLabel = new QLabel(card);
    m_pageLabel->setAlignment(Qt::AlignCenter);
    m_pageLabel->setStyleSheet(QStringLiteral("border:none;"));
    m_nextBtn = makeToolButton(card, tr("Next page"));
    m_nextBtn->setArrowType(Qt::RightArrow);
    footer->addWidget(m_prevBtn);
    footer->addWidget(m_pageLabel, 1);
    footer->addWidget(m_nextBtn);
    root->addLayout(footer);

    m_debounce = new QTimer(this);
    m_debounce->setSingleShot(true);
    m_debounce->setInterval(kSearchDebounceMs);
    connect(m_debounce, &QTimer::timeout, this, [this] {
        m_query = m_search->text().trimmed();
        m_page = 0;
        rebuild();
    });
    connect(m_search, &QLineEdit::textChanged, this, [this] { m_debounce->start(); });
    connect(m_search, &QLineEdit::returnPressed, this, [this] {
        QListWidgetItem *it = m_list->currentItem();
        if (!it && m_list->count() > 0) it = m_list->item(0);
        activateItem(it);
    });

    connect(closeBtn, &QToolButton::clicked, this, [this] { close(); });
    connect(m_backBtn, &QToolButton::clicked, this, [this] { goBackToGroups(); });
    connect(m_prevBtn, &QToolButton::clicked, this, [this] { changePage(-1); });
    connect(m_nextBtn, &QToolButton::clicked, this, [this] { changePage(+1); });
    connect(m_stopBtn, &QPushButton::clicked, this, [this] {
        if (m_cb.stopProfile) m_cb.stopProfile();
        close();
    });
    connect(m_list, &QListWidget::itemClicked, this, [this](QListWidgetItem *it) { activateItem(it); });
}

void TrayProfileSelector::ensureServerCache() {
    if (m_serverCacheBuilt) return;
    m_serverCacheBuilt = true;
    QList<int> allIds;
    for (auto gid : Configs::dataManager->groupsRepo->GetGroupsTabOrder()) {
        auto group = Configs::dataManager->groupsRepo->GetGroup(gid);
        if (!group || group->archive) continue;
        allIds.append(group->Profiles());
    }
    m_serverCache = Configs::dataManager->profilesRepo->GetProfileIDNameMappedBatch(allIds);
    m_serverCache = FixProfileDisplayName(m_serverCache);
    m_serverCacheLower.reserve(m_serverCache.size());
    for (const auto &[id, name] : m_serverCache) m_serverCacheLower.append(name.toLower());
}

void TrayProfileSelector::ensureRouteCache() {
    if (m_routeCacheBuilt) return;
    m_routeCacheBuilt = true;
    for (const auto &route : Configs::dataManager->routesRepo->GetAllRouteProfiles())
        m_routeCache.append({route->id, route->name});
    m_routeCacheLower.reserve(m_routeCache.size());
    for (const auto &[id, name] : m_routeCache) m_routeCacheLower.append(name.toLower());
}

void TrayProfileSelector::rebuild() {
    m_list->clear();
    const QString q = m_query.toLower();
    const bool searching = !q.isEmpty();

    struct Entry {
        int kind;
        int id;
        QString text;
        bool enabled;
        bool current;
    };
    QList<Entry> entries;
    QString title;
    bool showBack = false;
    bool showStop = false;
    QString stopText;

    if (m_mode == Routing) {
        title = tr("Select Routing");
        ensureRouteCache();
        const int cur = Configs::dataManager->settingsRepo->current_route_id;
        for (int i = 0; i < m_routeCache.size(); ++i) {
            if (searching && !m_routeCacheLower[i].contains(q)) continue;
            entries.append({KindRoute, m_routeCache[i].first, m_routeCache[i].second, true,
                            m_routeCache[i].first == cur});
        }
        if (entries.isEmpty())
            entries.append({0, -1, searching ? tr("No matches") : tr("No routing profiles"), false, false});
    } else {
        const bool running = m_cb.isRunning && m_cb.isRunning();
        const int rid = (running && m_cb.runningId) ? m_cb.runningId() : -1;
        if (searching) {
            title = tr("Select Profile");
            ensureServerCache();
            for (int i = 0; i < m_serverCache.size(); ++i) {
                if (!m_serverCacheLower[i].contains(q)) continue;
                entries.append({KindProfile, m_serverCache[i].first, m_serverCache[i].second, true,
                                m_serverCache[i].first == rid});
            }
            if (entries.isEmpty()) entries.append({0, -1, tr("No matches"), false, false});
        } else if (m_groupId < 0) {
            title = tr("Select Profile");
            if (running) {
                showStop = true;
                stopText = tr("Stop: %1").arg(m_cb.runningName ? m_cb.runningName() : QString());
            }
            const int rgid = (running && m_cb.runningGid) ? m_cb.runningGid() : -1;
            for (auto gid : Configs::dataManager->groupsRepo->GetGroupsTabOrder()) {
                auto group = Configs::dataManager->groupsRepo->GetGroup(gid);
                if (!group || group->archive || group->Profiles().isEmpty()) continue;
                entries.append({KindGroup, gid, group->name, true, gid == rgid});
            }
            if (entries.isEmpty()) entries.append({0, -1, tr("No profiles"), false, false});
        } else {
            auto group = Configs::dataManager->groupsRepo->GetGroup(m_groupId);
            if (!group || group->archive) {
                goBackToGroups();
                return;
            }
            showBack = true;
            title = group->name;
            auto mapped = Configs::dataManager->profilesRepo->GetProfileIDNameMappedBatch(group->Profiles());
            mapped = FixProfileDisplayName(mapped);
            for (const auto &[id, name] : mapped) {
                entries.append({KindProfile, id, name, true, id == rid});
            }
            if (entries.isEmpty()) entries.append({0, -1, tr("No profiles"), false, false});
        }
    }

    const int total = static_cast<int>(entries.size());
    const int pages = qMax(1, (total + kPerPage - 1) / kPerPage);
    m_page = qBound(0, m_page, pages - 1);
    const int start = m_page * kPerPage;
    const int end = qMin(start + kPerPage, total);

    for (int i = start; i < end; ++i) {
        const Entry &e = entries[i];
        auto *it = new QListWidgetItem(e.text, m_list);
        it->setData(RoleKind, e.kind);
        it->setData(RoleId, e.id);
        it->setData(RoleCurrent, e.current);
        if (!e.enabled) it->setFlags(it->flags() & ~Qt::ItemIsEnabled);
    }
    if (m_list->count() > 0) m_list->setCurrentRow(0);

    m_title->setText(title);
    m_backBtn->setVisible(showBack);
    m_stopBtn->setVisible(showStop);
    if (showStop) m_stopBtn->setText(stopText);

    const bool paginate = pages > 1;
    m_prevBtn->setVisible(paginate);
    m_nextBtn->setVisible(paginate);
    m_pageLabel->setVisible(paginate);
    if (paginate) {
        m_pageLabel->setText(tr("Page %1/%2").arg(m_page + 1).arg(pages));
        m_prevBtn->setEnabled(m_page > 0);
        m_nextBtn->setEnabled(m_page < pages - 1);
    }
}

void TrayProfileSelector::activateItem(QListWidgetItem *it) {
    if (!it || !(it->flags() & Qt::ItemIsEnabled)) return;
    const int kind = it->data(RoleKind).toInt();
    const int id = it->data(RoleId).toInt();
    if (kind == KindGroup) {
        m_groupId = id;
        m_page = 0;
        rebuild();
    } else if (kind == KindProfile) {
        if (m_cb.startProfile) m_cb.startProfile(id);
        close();
    } else if (kind == KindRoute) {
        if (m_cb.chooseRoute) m_cb.chooseRoute(id);
        close();
    }
}

void TrayProfileSelector::goBackToGroups() {
    m_groupId = -1;
    m_page = 0;
    rebuild();
}

void TrayProfileSelector::changePage(int delta) {
    m_page += delta;
    rebuild();
}

void TrayProfileSelector::popupAt(const QPoint &globalPos) {
    m_groupId = -1;
    m_page = 0;
    m_query.clear();
    m_search->blockSignals(true);
    m_search->clear();
    m_search->blockSignals(false);
    rebuild();
    adjustSize();

    QScreen *scr = QGuiApplication::screenAt(globalPos);
    if (!scr) scr = QGuiApplication::primaryScreen();
    const QRect avail = scr ? scr->availableGeometry() : QRect(0, 0, 1024, 768);
    const QSize sz = size();
    int x = globalPos.x();
    int y = globalPos.y();
    if (x + sz.width() > avail.right()) x = avail.right() - sz.width();
    if (y + sz.height() > avail.bottom()) y = avail.bottom() - sz.height();
    if (x < avail.left()) x = avail.left();
    if (y < avail.top()) y = avail.top();
    move(x, y);

    show();
    raise();
    activateWindow();
    m_search->setFocus();

    m_armed = false;
    QTimer::singleShot(150, this, [this] { m_armed = true; });
}

bool TrayProfileSelector::event(QEvent *e) {
    if (e->type() == QEvent::WindowDeactivate && m_armed) {
        close();
    }
    return QFrame::event(e);
}

void TrayProfileSelector::keyPressEvent(QKeyEvent *e) {
    if (e->key() == Qt::Key_Escape) {
        close();
        return;
    }
    QFrame::keyPressEvent(e);
}

bool TrayProfileSelector::eventFilter(QObject *obj, QEvent *e) {
    if (e->type() == QEvent::KeyPress) {
        auto *ke = static_cast<QKeyEvent *>(e);
        if (ke->key() == Qt::Key_Escape) {
            close();
            return true;
        }
        if (obj == m_list && (ke->key() == Qt::Key_Return || ke->key() == Qt::Key_Enter)) {
            activateItem(m_list->currentItem());
            return true;
        }
        if (obj == m_search && ke->key() == Qt::Key_Down && m_list->count() > 0) {
            m_list->setFocus();
            m_list->setCurrentRow(0);
            return true;
        }
    }
    return QFrame::eventFilter(obj, e);
}
