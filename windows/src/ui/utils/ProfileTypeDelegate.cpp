#include "include/ui/utils/ProfileTypeDelegate.h"

#include <QApplication>
#include <QPainter>

#include "include/ui/setting/ThemeManager.hpp"

namespace {
    constexpr int kBarWidth = 3;
    constexpr int kPillPadX = 8;
    constexpr int kPillPadY = 3;
}

void ProfileTypeDelegate::paint(QPainter *painter, const QStyleOptionViewItem &option, const QModelIndex &index) const {
    QStyleOptionViewItem opt = option;
    initStyleOption(&opt, index);
    const QString text = opt.text;
    opt.text.clear();
    opt.state &= ~QStyle::State_HasFocus;
    const QWidget *widget = opt.widget;
    QStyle *style = widget ? widget->style() : QApplication::style();
    style->drawControl(QStyle::CE_ItemViewItem, &opt, painter, widget);
    if (text.isEmpty()) return;

    const auto tk = ThemeManager::Tokens();
    const bool running = index.data(RunningRole).toBool();
    const QRect r = option.rect;

    painter->save();
    painter->setRenderHint(QPainter::Antialiasing, true);

    if (running) painter->fillRect(QRect(r.left(), r.top(), kBarWidth, r.height()), tk.accent);

    QFont f = opt.font;
    f.setPointSizeF(f.pointSizeF() > 0 ? f.pointSizeF() * 0.92 : f.pointSizeF());
    painter->setFont(f);
    const QFontMetrics fm(f);
    const int maxTextW = qMax(0, r.width() - 2 * ProfileTextDelegate::TextPadding - 2 * kPillPadX);
    const QString shown = fm.elidedText(text, Qt::ElideRight, maxTextW);
    const int pillW = fm.horizontalAdvance(shown) + 2 * kPillPadX;
    const int pillH = fm.height() + 2 * kPillPadY;
    const QRectF pill(r.left() + ProfileTextDelegate::TextPadding, r.center().y() - pillH / 2.0 + 0.5, pillW, pillH);

    QColor bg = tk.border;
    if (opt.state & QStyle::State_Selected) bg = tk.surface;
    painter->setPen(Qt::NoPen);
    painter->setBrush(bg);
    painter->drawRoundedRect(pill, pillH / 2.0, pillH / 2.0);

    const QVariant fg = index.data(Qt::ForegroundRole);
    painter->setPen(fg.canConvert<QColor>() && fg.value<QColor>().isValid() && !running ? fg.value<QColor>() : tk.muted);
    painter->drawText(pill, Qt::AlignCenter, shown);
    painter->restore();
}

QSize ProfileTypeDelegate::sizeHint(const QStyleOptionViewItem &option, const QModelIndex &index) const {
    QSize s = QStyledItemDelegate::sizeHint(option, index);
    QFont f = option.font;
    if (f.pointSizeF() > 0) f.setPointSizeF(f.pointSizeF() * 0.92);
    const QFontMetrics fm(f);
    const int w = fm.horizontalAdvance(index.data(Qt::DisplayRole).toString()) + 2 * kPillPadX + 2 * ProfileTextDelegate::TextPadding + 4;
    return {w, qMax(s.height(), fm.height() + 2 * kPillPadY + 8)};
}

void ProfileTextDelegate::paint(QPainter *painter, const QStyleOptionViewItem &option, const QModelIndex &index) const {
    QStyleOptionViewItem opt = option;
    initStyleOption(&opt, index);
    const QString text = opt.text;
    opt.text.clear();
    opt.state &= ~QStyle::State_HasFocus;
    const QWidget *widget = opt.widget;
    QStyle *style = widget ? widget->style() : QApplication::style();
    style->drawControl(QStyle::CE_ItemViewItem, &opt, painter, widget);
    if (text.isEmpty()) return;

    const QRect textRect = opt.rect.adjusted(TextPadding, 0, -TextPadding, 0);
    const QPalette::ColorGroup group = !(opt.state & QStyle::State_Enabled) ? QPalette::Disabled
                                     : (opt.state & QStyle::State_Active) ? QPalette::Normal : QPalette::Inactive;
    painter->save();
    painter->setFont(opt.font);
    painter->setPen(opt.palette.color(group, (opt.state & QStyle::State_Selected) ? QPalette::HighlightedText : QPalette::Text));
    const QFontMetrics fm(opt.font);
    const auto hAlign = opt.displayAlignment & Qt::AlignHorizontal_Mask;
    painter->drawText(textRect, hAlign | Qt::AlignVCenter | Qt::TextSingleLine, fm.elidedText(text, opt.textElideMode, textRect.width()));
    painter->restore();
}

QSize ProfileTextDelegate::sizeHint(const QStyleOptionViewItem &option, const QModelIndex &index) const {
    QStyleOptionViewItem opt = option;
    initStyleOption(&opt, index);
    const QFontMetrics fm(opt.font);
    const QSize base = QStyledItemDelegate::sizeHint(option, index);
    return {fm.horizontalAdvance(opt.text) + 2 * TextPadding + 4, base.height()};
}
