package com.marvserver.folia_phantom_3.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private String title;
    private String header;
    private String description;
    private String footer;
    private Theme theme = new Theme();

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getHeader() {
        return header;
    }

    public void setHeader(String header) {
        this.header = header;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getFooter() {
        return footer;
    }

    public void setFooter(String footer) {
        this.footer = footer;
    }

    public Theme getTheme() {
        return theme;
    }

    public void setTheme(Theme theme) {
        this.theme = theme;
    }

    public static class Theme {
        private String bgColor;
        private String primaryColor;
        private String textColor;
        private String containerBg;
        private String borderColor;
        private String buttonHoverBg;

        public String getBgColor() {
            return bgColor;
        }

        public void setBgColor(String bgColor) {
            this.bgColor = bgColor;
        }

        public String getPrimaryColor() {
            return primaryColor;
        }

        public void setPrimaryColor(String primaryColor) {
            this.primaryColor = primaryColor;
        }

        public String getTextColor() {
            return textColor;
        }

        public void setTextColor(String textColor) {
            this.textColor = textColor;
        }

        public String getContainerBg() {
            return containerBg;
        }

        public void setContainerBg(String containerBg) {
            this.containerBg = containerBg;
        }

        public String getBorderColor() {
            return borderColor;
        }

        public void setBorderColor(String borderColor) {
            this.borderColor = borderColor;
        }

        public String getButtonHoverBg() {
            return buttonHoverBg;
        }

        public void setButtonHoverBg(String buttonHoverBg) {
            this.buttonHoverBg = buttonHoverBg;
        }
    }
}
