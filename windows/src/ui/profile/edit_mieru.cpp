#include "include/ui/profile/edit_mieru.h"

#include "include/global/GuiUtils.hpp"

EditMieru::EditMieru(QWidget *parent) : QWidget(parent), ui(new Ui::EditMieru) {
    ui->setupUi(this);
    ui->transport->addItems(Configs::mieruTransports);
    FillOptionalCombo(ui->multiplexing, Configs::mieruMultiplexing, tr("Default"));
}

EditMieru::~EditMieru() {
    delete ui;
}

void EditMieru::onStart(std::shared_ptr<Configs::Profile> _ent) {
    this->ent = _ent;
    auto outbound = this->ent->Mieru();

    ui->username->setText(outbound->username);
    ui->password->setText(outbound->password);
    ui->transport->setCurrentText(outbound->transport.isEmpty() ? "TCP" : outbound->transport);
    SelectComboData(ui->multiplexing, outbound->multiplexing);
    ui->traffic_pattern->setText(outbound->traffic_pattern);
    ui->server_ports->setText(outbound->server_ports);
}

bool EditMieru::onEnd() {
    auto outbound = this->ent->Mieru();

    outbound->username = ui->username->text();
    outbound->password = ui->password->text();
    outbound->transport = ui->transport->currentText();
    outbound->multiplexing = ui->multiplexing->currentData().toString();
    outbound->traffic_pattern = ui->traffic_pattern->text();
    outbound->server_ports = ui->server_ports->text();

    return true;
}
