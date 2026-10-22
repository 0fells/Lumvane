#include "include/ui/widget/LogHighlighter.hpp"

#define TO_EOL "(([\\s\\S]*)|([\\d\\D]*)|([\\w\\W]*))$"
#define REGEX_IPV6_ADDR \
    R"(\[\s*((([0-9A-Fa-f]{1,4}:){7}([0-9A-Fa-f]{1,4}|:))|(([0-9A-Fa-f]{1,4}:){6}(:[0-9A-Fa-f]{1,4}|((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){5}(((:[0-9A-Fa-f]{1,4}){1,2})|:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3})|:))|(([0-9A-Fa-f]{1,4}:){4}(((:[0-9A-Fa-f]{1,4}){1,3})|((:[0-9A-Fa-f]{1,4})?:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){3}(((:[0-9A-Fa-f]{1,4}){1,4})|((:[0-9A-Fa-f]{1,4}){0,2}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){2}(((:[0-9A-Fa-f]{1,4}){1,5})|((:[0-9A-Fa-f]{1,4}){0,3}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(([0-9A-Fa-f]{1,4}:){1}(((:[0-9A-Fa-f]{1,4}){1,6})|((:[0-9A-Fa-f]{1,4}){0,4}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:))|(:(((:[0-9A-Fa-f]{1,4}){1,7})|((:[0-9A-Fa-f]{1,4}){0,5}:((25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)(\.(25[0-5]|2[0-4]\d|1\d\d|[1-9]?\d)){3}))|:)))(%.+)?\s*\])"
#define REGEX_IPV4_ADDR \
    R"((\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5])\.(\d{1,2}|1\d\d|2[0-4]\d|25[0-5]))"
#define REGEX_PORT_NUMBER R"(([0-9]|[1-9]\d{1,3}|[1-5]\d{4}|6[0-5]{2}[0-3][0-5])*)"

namespace Lumvane::ui {
    SyntaxHighlighter::SyntaxHighlighter(bool darkMode, QTextDocument *parent) : QSyntaxHighlighter(parent) {
        HighlightingRule rule;

        if (darkMode) {
            tcpudpFormat.setForeground(QColor(0x56, 0xC8, 0xD8));
            ipHostFormat.setForeground(QColor(0xE5, 0xC0, 0x7B));
            warningFormat.setForeground(QColor(0xF5, 0xA6, 0x4B));
            warningFormat2.setForeground(QColor(0xF5, 0xC2, 0x4B));
            rejectedFormat.setForeground(QColor(0xFF, 0x7A, 0x85));
        } else {
            tcpudpFormat.setForeground(QColor(0x0E, 0x74, 0x90));
            ipHostFormat.setForeground(QColor(0x8A, 0x55, 0x00));
            warningFormat.setForeground(QColor(0xC2, 0x41, 0x0C));
            warningFormat2.setForeground(QColor(0xB4, 0x53, 0x09));
            rejectedFormat.setForeground(QColor(0xC6, 0x28, 0x28));
        }
        const QColor okColor = darkMode ? QColor(0x7D, 0xD3, 0xA8) : QColor(0x1B, 0x7F, 0x55);

        acceptedFormat.setForeground(okColor);
        acceptedFormat.setFontItalic(true);
        acceptedFormat.setFontWeight(QFont::Bold);
        rule.pattern = QRegularExpression("\\saccepted\\s");
        rule.format = acceptedFormat;
        highlightingRules.append(rule);
        rejectedFormat.setFontWeight(QFont::Bold);
        rule.pattern = QRegularExpression("\\srejected\\s" TO_EOL);
        rule.format = rejectedFormat;
        highlightingRules.append(rule);
        dateFormat.setForeground(darkMode ? QColor(0x8A, 0x90, 0xAE) : QColor(0x6B, 0x71, 0x90));
        rule.pattern = QRegularExpression("\\d\\d\\d\\d/\\d\\d/\\d\\d");
        rule.format = dateFormat;
        highlightingRules.append(rule);
        timeFormat.setForeground(darkMode ? QColor(0x8A, 0x90, 0xAE) : QColor(0x6B, 0x71, 0x90));
        rule.pattern = QRegularExpression("\\d\\d:\\d\\d:\\d\\d");
        rule.format = timeFormat;
        highlightingRules.append(rule);
        debugFormat.setForeground(darkMode ? QColor(0x6E, 0x74, 0x90) : QColor(0x8A, 0x90, 0xA6));
        rule.pattern = QRegularExpression("D[Ee][Bb][Uu][Gg].*?");
        rule.format = debugFormat;
        highlightingRules.append(rule);
        infoFormat.setForeground(darkMode ? QColor(0x7D, 0xD3, 0xA8) : QColor(0x1B, 0x7F, 0x55));
        rule.pattern = QRegularExpression("I[Nn][Ff][Oo].*?");
        rule.format = infoFormat;
        highlightingRules.append(rule);
        warningFormat.setFontWeight(QFont::Bold);
        warningFormat2.setFontWeight(QFont::Bold);
        rule.pattern = QRegularExpression("W[Aa][Rr][Nn].*?");
        rule.format = warningFormat2;
        highlightingRules.append(rule);
        rule.pattern = QRegularExpression("E[Rr][Rr][Oo][Rr].*?");
        rule.format = rejectedFormat;
        highlightingRules.append(rule);

        v2rayComponentFormat.setForeground(okColor);
        rule.pattern = QRegularExpression(R"( (\w+\/)+\w+: )");
        rule.format = v2rayComponentFormat;
        highlightingRules.append(rule);
        singboxComponentFormat.setForeground(darkMode ? QColor(0xA9, 0xB3, 0xFF) : QColor(0x3A, 0x47, 0xE0));
        rule.pattern = QRegularExpression(R"((?<=\] )[a-z][\w-]*(/[\w-]+)*(\[[^\]]*\])?:(?= ))");
        rule.format = singboxComponentFormat;
        highlightingRules.append(rule);
        rule.pattern = QRegularExpression(">>>>+");
        rule.format = warningFormat;
        highlightingRules.append(rule);
        rule.pattern = QRegularExpression("<<<<+");
        rule.format = warningFormat;
        highlightingRules.append(rule);
        rule.pattern = QRegularExpression("error");
        rule.format = rejectedFormat;
        highlightingRules.append(rule);

        {
            rule.pattern = QRegularExpression(REGEX_IPV4_ADDR ":" REGEX_PORT_NUMBER);
            rule.pattern.setPatternOptions(QRegularExpression::ExtendedPatternSyntaxOption);
            rule.format = ipHostFormat;
            highlightingRules.append(rule);
            rule.pattern = QRegularExpression(REGEX_IPV6_ADDR ":" REGEX_PORT_NUMBER);
            rule.pattern.setPatternOptions(QRegularExpression::ExtendedPatternSyntaxOption);
            rule.format = ipHostFormat;
            highlightingRules.append(rule);
            rule.pattern = QRegularExpression("([a-zA-Z0-9]([a-zA-Z0-9\\-]{0,61}[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,6}(/|):" REGEX_PORT_NUMBER);
            rule.pattern.setPatternOptions(QRegularExpression::PatternOption::ExtendedPatternSyntaxOption);
            rule.format = ipHostFormat;
            highlightingRules.append(rule);
        }

        for (const auto &pattern: {"tcp:", "udp:"}) {
            tcpudpFormat.setFontWeight(QFont::Bold);
            rule.pattern = QRegularExpression(pattern);
            rule.format = tcpudpFormat;
            highlightingRules.append(rule);
        }
    }

    void SyntaxHighlighter::highlightBlock(const QString &text) {
        for (const HighlightingRule &rule: highlightingRules) {
            QRegularExpressionMatchIterator matchIterator = rule.pattern.globalMatch(text);

            while (matchIterator.hasNext()) {
                QRegularExpressionMatch match = matchIterator.next();
                setFormat(match.capturedStart(), match.capturedLength(), rule.format);
            }
        }

        setCurrentBlockState(0);
    }
}
