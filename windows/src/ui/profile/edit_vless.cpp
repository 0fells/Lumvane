#include "include/ui/profile/edit_vless.h"
#include "include/global/GuiUtils.hpp"

EditVless::EditVless(QWidget *parent)
    : QWidget(parent),
      ui(new Ui::EditVless) {
    ui->setupUi(this);

    _flow = ui->flow;
    ui->flow->addItem(tr("None"), QString());
    for (const auto &flow : Configs::vlessFlows) ui->flow->addItem(flow, flow);
    FillOptionalCombo(ui->packet_encoding, Configs::vPacketEncoding, tr("Default"));
}

EditVless::~EditVless() {
    delete ui;
}

void EditVless::onStart(std::shared_ptr<Configs::Profile> _ent) {
    this->ent = _ent;
    auto outbound = this->ent->VLESS();

    ui->uuid->setText(outbound->uuid);
    ui->flow->setCurrentIndex(qMax(0, ui->flow->findData(outbound->flow)));
    SelectComboData(ui->packet_encoding, outbound->packet_encoding);
}

bool EditVless::onEnd() {
    auto outbound = this->ent->VLESS();

    outbound->uuid = ui->uuid->text();
    outbound->flow = ui->flow->currentData().toString();
    outbound->packet_encoding = ui->packet_encoding->currentData().toString();
    return true;
}