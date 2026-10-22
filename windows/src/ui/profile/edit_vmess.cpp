#include "include/ui/profile/edit_vmess.h"
#include "include/global/GuiUtils.hpp"

#include <QUuid>

EditVMess::EditVMess(QWidget *parent) : QWidget(parent), ui(new Ui::EditVMess) {
    ui->setupUi(this);
    connect(ui->uuidgen, &QPushButton::clicked, this, [=,this] { ui->uuid->setText(QUuid::createUuid().toString().remove("{").remove("}")); });
    FillOptionalCombo(ui->packet_encoding, Configs::vPacketEncoding, tr("Default"));
}

EditVMess::~EditVMess() {
    delete ui;
}

void EditVMess::onStart(std::shared_ptr<Configs::Profile> _ent) {
    this->ent = _ent;
    auto outbound = this->ent->VMess();

    ui->uuid->setText(outbound->uuid);
    ui->aid->setText(Int2String(outbound->alter_id));
    SelectComboData(ui->packet_encoding, outbound->packet_encoding);
    ui->security->setCurrentText(outbound->security);
}

bool EditVMess::onEnd() {
    auto outbound = this->ent->VMess();

    outbound->uuid = ui->uuid->text();
    outbound->alter_id = ui->aid->text().toInt();
    outbound->packet_encoding = ui->packet_encoding->currentData().toString();
    outbound->security = ui->security->currentText();

    return true;
}
