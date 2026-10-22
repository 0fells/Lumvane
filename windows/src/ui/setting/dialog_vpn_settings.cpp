#include "include/ui/setting/dialog_vpn_settings.h"

#include "include/global/GuiUtils.hpp"
#include "include/global/Configs.hpp"
#include "include/ui/mainwindow_interface.h"
#include "include/sys/windows/WinVersion.h"

#include <QMessageBox>
#include <QHostAddress>
#include <QIntValidator>
#include <QPushButton>


#define ADJUST_SIZE runOnThread([=,this] { adjustSize(); adjustPosition(mainwindow); }, this);

namespace {
    const QString kDefaultTunIPv4CIDR = "172.19.0.1/24";
    const QString kDefaultTunIPv6CIDR = "fdfe:dcba:9876::1/96";
    constexpr int kMinMTU = 1000;
    constexpr int kMaxMTU = 10000;

    bool IsValidCIDR(const QString &cidr, const QAbstractSocket::NetworkLayerProtocol protocol) {
        const auto parts = cidr.trimmed().split("/");
        if (parts.size() != 2) return false;

        bool ok = false;
        const int prefix = parts[1].toInt(&ok);
        if (!ok) return false;

        QHostAddress host;
        if (!host.setAddress(parts[0].trimmed())) return false;
        if (host.protocol() != protocol) return false;

        if (protocol == QAbstractSocket::IPv4Protocol) return prefix >= 0 && prefix <= 32;
        if (protocol == QAbstractSocket::IPv6Protocol) return prefix >= 0 && prefix <= 128;
        return false;
    }

    bool IsValidMTU(const QString &text) {
        bool ok = false;
        const int mtu = text.toInt(&ok);
        return ok && mtu >= kMinMTU && mtu <= kMaxMTU;
    }

    void MarkField(QWidget *field, bool valid, const QString &hint) {
        field->setStyleSheet(valid ? QString()
                                   : QString("%1 { border: 1px solid %2; }").arg(field->metaObject()->className(), errorTextColor()));
        field->setToolTip(valid ? QString() : hint);
    }
}

DialogVPNSettings::DialogVPNSettings(QWidget *parent) : QDialog(parent), ui(new Ui::DialogVPNSettings) {
    ui->setupUi(this);
    ADD_ASTERISK(this);

    ui->vpn_implementation->addItems(Configs::VPNImplementation::VPNImplementation);
    if (WinVersion::IsBuildNumGreaterOrEqual(BuildNumber::Windows_10_1507)) {
        ui->vpn_implementation->setCurrentText(Configs::dataManager->settingsRepo->vpn_implementation);
    } else {
        ui->vpn_implementation->setCurrentText("gvisor");
        ui->vpn_implementation->setEnabled(false);
    }
    ui->vpn_mtu->setValidator(new QIntValidator(kMinMTU, kMaxMTU, this));
    ui->vpn_mtu->setCurrentText(Int2String(Configs::dataManager->settingsRepo->vpn_mtu));
    ui->vpn_ipv6->setChecked(Configs::dataManager->settingsRepo->vpn_ipv6);
    ui->strict_route->setChecked(Configs::dataManager->settingsRepo->vpn_strict_route);
    ui->tun_routing->setChecked(Configs::dataManager->settingsRepo->enable_tun_routing);
    ui->tun_ipv4_cidr->setText(Configs::dataManager->settingsRepo->vpn_tun_ipv4_cidr);
    ui->tun_ipv6_cidr->setText(Configs::dataManager->settingsRepo->vpn_tun_ipv6_cidr);
    ui->disable_priv_range->setChecked(Configs::dataManager->settingsRepo->disable_private_range_bypass);
    ui->auto_redirect->setChecked(Configs::dataManager->settingsRepo->vpn_auto_redirect);
    ui->auto_redirect->hide();

    connect(ui->tun_ipv4_cidr, &QLineEdit::textChanged, this, &DialogVPNSettings::validateInput);
    connect(ui->tun_ipv6_cidr, &QLineEdit::textChanged, this, &DialogVPNSettings::validateInput);
    connect(ui->vpn_mtu, &QComboBox::editTextChanged, this, &DialogVPNSettings::validateInput);
    validateInput();
    ADJUST_SIZE
}

DialogVPNSettings::~DialogVPNSettings() {
    delete ui;
}

bool DialogVPNSettings::validateInput() {
    const bool v4 = IsValidCIDR(ui->tun_ipv4_cidr->text(), QAbstractSocket::IPv4Protocol);
    const bool v6 = IsValidCIDR(ui->tun_ipv6_cidr->text(), QAbstractSocket::IPv6Protocol);
    const bool mtu = IsValidMTU(ui->vpn_mtu->currentText());
    MarkField(ui->tun_ipv4_cidr, v4, tr("Expected an IPv4 address with a prefix length, for example %1").arg(kDefaultTunIPv4CIDR));
    MarkField(ui->tun_ipv6_cidr, v6, tr("Expected an IPv6 address with a prefix length, for example %1").arg(kDefaultTunIPv6CIDR));
    MarkField(ui->vpn_mtu, mtu, tr("MTU must be between %1 and %2").arg(kMinMTU).arg(kMaxMTU));
    const bool valid = v4 && v6 && mtu;
    if (auto *ok = ui->buttonBox->button(QDialogButtonBox::Ok)) ok->setEnabled(valid);
    return valid;
}

void DialogVPNSettings::accept() {
    if (!validateInput()) return;

    Configs::dataManager->settingsRepo->vpn_implementation = ui->vpn_implementation->currentText();
    Configs::dataManager->settingsRepo->vpn_mtu = ui->vpn_mtu->currentText().toInt();
    Configs::dataManager->settingsRepo->vpn_ipv6 = ui->vpn_ipv6->isChecked();
    Configs::dataManager->settingsRepo->vpn_strict_route = ui->strict_route->isChecked();
    Configs::dataManager->settingsRepo->enable_tun_routing = ui->tun_routing->isChecked();
    Configs::dataManager->settingsRepo->vpn_tun_ipv4_cidr = ui->tun_ipv4_cidr->text().trimmed();
    Configs::dataManager->settingsRepo->vpn_tun_ipv6_cidr = ui->tun_ipv6_cidr->text().trimmed();
    Configs::dataManager->settingsRepo->disable_private_range_bypass = ui->disable_priv_range->isChecked();
    Configs::dataManager->settingsRepo->vpn_auto_redirect = ui->auto_redirect->isChecked();
    MW_dialog_message(MwMessage::UpdateSettings, {MwArg::Vpn});
    QDialog::accept();
}

void DialogVPNSettings::on_restore_default_addresses_clicked() {
    ui->tun_ipv4_cidr->setText(kDefaultTunIPv4CIDR);
    ui->tun_ipv6_cidr->setText(kDefaultTunIPv6CIDR);
}

void DialogVPNSettings::on_troubleshooting_clicked() {
    QMessageBox msg(
        QMessageBox::Information,
        tr("Troubleshooting"),
        tr("If Tun does not start, you can force reset the Core process here.\n\n"
           "If websites do not open while DNS works, switch the stack to gvisor. "
           "The log shows inbound/tun lines when traffic goes through Tun."),
        QMessageBox::NoButton,
        this
    );
    auto *reset = msg.addButton(tr("Reset"), QMessageBox::ActionRole);
    auto *cancel = msg.addButton(tr("Cancel"), QMessageBox::RejectRole);

    msg.setDefaultButton(cancel);
    msg.setEscapeButton(cancel);

    msg.exec();
    if (msg.clickedButton() == reset) {
        GetMainWindow()->StopVPNProcess();
    }
}
