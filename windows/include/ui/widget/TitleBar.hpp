#pragma once

#include <QWidget>

class CaptionButton;

class TitleBar : public QWidget {
    Q_OBJECT

public:
    explicit TitleBar(QWidget *window);

    void setCustomFrame(bool on);
    bool customFrame() const { return customFrame_; }

    void applyTheme();

    bool handleNativeEvent(void *message, qintptr *result);

    bool frameActive() const { return customFrame_ ? frameActive_ : window_->isActiveWindow(); }

protected:
    void paintEvent(QPaintEvent *event) override;
    void changeEvent(QEvent *event) override;
    bool eventFilter(QObject *watched, QEvent *event) override;

private:
    void updateMaximizeButton();

    QWidget *window_;
    CaptionButton *minimize_;
    CaptionButton *maximize_;
    CaptionButton *close_;
    bool customFrame_ = false;
    bool frameActive_ = true;
};
