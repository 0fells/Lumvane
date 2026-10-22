#include "include/ui/widget/EmptyProfilesView.hpp"

#include <QHBoxLayout>
#include <QLabel>
#include <QPushButton>
#include <QVBoxLayout>

#include "include/ui/setting/Icon.hpp"
#include "include/ui/setting/ThemeManager.hpp"

EmptyProfilesView::EmptyProfilesView(QWidget *parent) : QWidget(parent) {
    setAttribute(Qt::WA_StyledBackground, true);

    icon_ = new QLabel(this);
    icon_->setFixedSize(56, 56);
    icon_->setAlignment(Qt::AlignCenter);

    title_ = new QLabel(tr("No profiles yet"), this);
    title_->setAlignment(Qt::AlignCenter);
    QFont tf = title_->font();
    tf.setPointSizeF(tf.pointSizeF() * 1.25);
    tf.setWeight(QFont::DemiBold);
    title_->setFont(tf);

    text_ = new QLabel(tr("Copy a server link or a subscription URL and paste it here. "
                          "vless://, hy2://, ss:// and other share links are supported."), this);
    text_->setAlignment(Qt::AlignCenter);
    text_->setWordWrap(true);
    text_->setFixedWidth(440);

    paste_ = new QPushButton(tr("Paste from clipboard · Ctrl+V"), this);
    paste_->setDefault(true);
    subscription_ = new QPushButton(tr("Add subscription"), this);
    scanQr_ = new QPushButton(tr("Scan QR code"), this);
    connect(paste_, &QPushButton::clicked, this, &EmptyProfilesView::pasteRequested);
    connect(subscription_, &QPushButton::clicked, this, &EmptyProfilesView::subscriptionRequested);
    connect(scanQr_, &QPushButton::clicked, this, &EmptyProfilesView::scanQrRequested);

    auto *buttons = new QHBoxLayout;
    buttons->setSpacing(8);
    buttons->addStretch();
    buttons->addWidget(paste_);
    buttons->addWidget(subscription_);
    buttons->addWidget(scanQr_);
    buttons->addStretch();

    auto *layout = new QVBoxLayout(this);
    layout->setSpacing(10);
    layout->addStretch();
    layout->addWidget(icon_, 0, Qt::AlignHCenter);
    layout->addWidget(title_);
    layout->addWidget(text_, 0, Qt::AlignHCenter);
    layout->addSpacing(6);
    layout->addLayout(buttons);
    layout->addStretch();

    applyTheme();
}

void EmptyProfilesView::applyTheme() {
    const auto tk = ThemeManager::Tokens();
    setStyleSheet(QString("EmptyProfilesView { background: %1; }").arg(tk.surface.name()));
    icon_->setStyleSheet(QString("background: %1; border-radius: 14px;").arg(tk.accentSoft.name()));
    icon_->setPixmap(Icon::Themed("empty-profiles", tk.accent, 26).pixmap(QSize(26, 26), devicePixelRatioF()));
    text_->setStyleSheet(QString("color: %1;").arg(tk.muted.name()));
    text_->setFixedHeight(text_->heightForWidth(text_->width()));
    paste_->setStyleSheet(QString("QPushButton { background: %1; color: %2; border: 0; border-radius: 6px; padding: 6px 14px; font-weight: 600; }"
                                  "QPushButton:hover { background: %3; }")
                              .arg(tk.accent.name(), tk.onAccent.name(),
                                   (tk.dark ? tk.accent.lighter(112) : tk.accent.darker(112)).name()));
}

