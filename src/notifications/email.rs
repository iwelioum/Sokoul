use lettre::message::header::ContentType;
use lettre::transport::smtp::authentication::Credentials;
use lettre::transport::smtp::SmtpTransport;
use lettre::{Message, Transport};
use serde::{Deserialize, Serialize};
use std::error::Error;

/// Represents a security event for digest emails
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct SecurityEvent {
    pub timestamp: String,
    pub event_type: String,
    pub severity: String,
    pub description: String,
    pub details: Option<String>,
}

/// Email service for sending notifications
#[derive(Clone)]
pub struct EmailService {
    smtp_host: String,
    smtp_port: u16,
    smtp_user: String,
    smtp_password: String,
    pub enabled: bool,
}

impl EmailService {
    /// Initialize EmailService from environment variables
    pub fn from_env() -> Self {
        let enabled = std::env::var("SMTP_ENABLED")
            .map(|v| v.to_lowercase() == "true")
            .unwrap_or(false);

        let smtp_host = std::env::var("SMTP_HOST").unwrap_or_else(|_| "smtp.gmail.com".to_string());
        let smtp_port = std::env::var("SMTP_PORT")
            .ok()
            .and_then(|p| p.parse::<u16>().ok())
            .unwrap_or(587);
        let smtp_user = std::env::var("SMTP_USER").unwrap_or_default();
        let smtp_password = std::env::var("SMTP_PASSWORD").unwrap_or_default();

        Self {
            smtp_host,
            smtp_port,
            smtp_user,
            smtp_password,
            enabled,
        }
    }

    /// Create SMTP transport
    fn create_transport(&self) -> Result<SmtpTransport, Box<dyn Error>> {
        let creds = Credentials::new(
            self.smtp_user.clone().into(),
            self.smtp_password.clone().into(),
        );

        let transport = SmtpTransport::relay(&self.smtp_host)?
            .port(self.smtp_port)
            .credentials(creds)
            .build();

        Ok(transport)
    }

    /// Send a critical security alert email
    pub async fn send_critical_alert(
        &self,
        admin_email: &str,
        url: &str,
        risk_level: &str,
        reason: &str,
    ) -> Result<(), Box<dyn Error>> {
        if !self.enabled {
            return Err("Email notifications are disabled".into());
        }

        let subject = format!(
            "🚨 CRITICAL Security Alert - {} Risk Detected",
            risk_level.to_uppercase()
        );

        let html_body = format!(
            r#"
<!DOCTYPE html>
<html>
<head>
    <style>
        body {{ font-family: Arial, sans-serif; }}
        .alert {{ background-color: #f8d7da; border: 1px solid #f5c6cb; border-radius: 4px; padding: 12px; margin: 10px 0; }}
        .alert-title {{ color: #721c24; font-weight: bold; font-size: 16px; }}
        .details {{ background-color: #f5f5f5; padding: 10px; border-left: 4px solid #dc3545; margin: 10px 0; }}
        .details-item {{ margin: 8px 0; }}
        .label {{ font-weight: bold; color: #333; }}
        .value {{ color: #666; word-break: break-all; }}
    </style>
</head>
<body>
    <div class="alert">
        <div class="alert-title">Critical Security Alert</div>
    </div>
    
    <div class="details">
        <div class="details-item">
            <span class="label">Risk Level:</span>
            <span class="value">{}</span>
        </div>
        <div class="details-item">
            <span class="label">URL:</span>
            <span class="value">{}</span>
        </div>
        <div class="details-item">
            <span class="label">Reason:</span>
            <span class="value">{}</span>
        </div>
        <div class="details-item">
            <span class="label">Timestamp:</span>
            <span class="value">{}</span>
        </div>
    </div>
    
    <p style="color: #666; font-size: 12px; margin-top: 20px;">
        This is an automated security alert from Sokoul. Please review and take appropriate action.
    </p>
</body>
</html>
            "#,
            risk_level,
            url,
            reason,
            chrono::Utc::now().to_rfc3339()
        );

        let email = Message::builder()
            .from(
                self.smtp_user
                    .parse()
                    .unwrap_or_else(|_| "sokoul@example.com".parse().unwrap()),
            )
            .to(admin_email.parse()?)
            .subject(&subject)
            .header(ContentType::TEXT_HTML)
            .body(html_body)?;

        let transport = self.create_transport()?;
        transport.send(&email)?;

        Ok(())
    }

    /// Send a security digest email with multiple events
    pub async fn send_digest(
        &self,
        admin_email: &str,
        events: Vec<SecurityEvent>,
    ) -> Result<(), Box<dyn Error>> {
        if !self.enabled {
            return Err("Email notifications are disabled".into());
        }

        if events.is_empty() {
            return Err("No events to digest".into());
        }

        let event_count = events.len();
        let subject = format!("Security Digest - {} Events", event_count);

        let mut events_html = String::new();
        for (_idx, event) in events.iter().enumerate() {
            let severity_color = match event.severity.to_lowercase().as_str() {
                "critical" => "#dc3545",
                "high" => "#fd7e14",
                "medium" => "#ffc107",
                "low" => "#17a2b8",
                _ => "#6c757d",
            };

            events_html.push_str(&format!(
                r#"
                <div style="border: 1px solid #ddd; border-left: 4px solid {}; padding: 12px; margin: 10px 0; background-color: #fafafa;">
                    <div style="font-weight: bold; color: {};">[{}] {}</div>
                    <div style="font-size: 12px; color: #666; margin: 5px 0;">{}</div>
                    <div style="margin-top: 8px; color: #333;">{}</div>
                    {}
                </div>
                "#,
                severity_color,
                severity_color,
                event.severity.to_uppercase(),
                event.event_type,
                event.timestamp,
                event.description,
                event
                    .details
                    .as_ref()
                    .map(|d| format!(r#"<div style="font-size: 11px; color: #666; margin-top: 5px; padding-top: 5px; border-top: 1px solid #e0e0e0;"><strong>Details:</strong> {}</div>"#, d))
                    .unwrap_or_default()
            ));
        }

        let html_body = format!(
            r#"
<!DOCTYPE html>
<html>
<head>
    <style>
        body {{ font-family: Arial, sans-serif; color: #333; }}
        .header {{ background-color: #007bff; color: white; padding: 20px; border-radius: 4px; margin-bottom: 20px; }}
        .header h1 {{ margin: 0; font-size: 24px; }}
        .summary {{ background-color: #e7f3ff; border: 1px solid #b3d9ff; border-radius: 4px; padding: 15px; margin: 15px 0; }}
        .events-container {{ margin: 20px 0; }}
    </style>
</head>
<body>
    <div class="header">
        <h1>Security Digest Report</h1>
        <p style="margin: 5px 0; opacity: 0.9;">Sokoul Security Monitoring</p>
    </div>
    
    <div class="summary">
        <strong>Total Events:</strong> {}<br>
        <strong>Report Generated:</strong> {}<br>
        <strong>Period:</strong> Latest Digest
    </div>
    
    <div class="events-container">
        <h2 style="border-bottom: 2px solid #007bff; padding-bottom: 10px;">Security Events</h2>
        {}
    </div>
    
    <hr style="border: none; border-top: 1px solid #ddd; margin: 20px 0;">
    
    <p style="color: #666; font-size: 12px;">
        This is an automated security digest from Sokoul. Please review the events and take appropriate action if needed.
    </p>
    <p style="color: #999; font-size: 11px;">
        Do not reply to this email. If you have questions, contact your system administrator.
    </p>
</body>
</html>
            "#,
            event_count,
            chrono::Utc::now().to_rfc3339(),
            events_html
        );

        let email = Message::builder()
            .from(
                self.smtp_user
                    .parse()
                    .unwrap_or_else(|_| "sokoul@example.com".parse().unwrap()),
            )
            .to(admin_email.parse()?)
            .subject(&subject)
            .header(ContentType::TEXT_HTML)
            .body(html_body)?;

        let transport = self.create_transport()?;
        transport.send(&email)?;

        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_email_service_from_env() {
        std::env::set_var("SMTP_ENABLED", "false");
        std::env::set_var("SMTP_HOST", "smtp.test.com");
        std::env::set_var("SMTP_PORT", "465");

        let service = EmailService::from_env();
        assert_eq!(service.smtp_host, "smtp.test.com");
        assert_eq!(service.smtp_port, 465);
        assert!(!service.enabled);
    }

    #[test]
    fn test_security_event_creation() {
        let event = SecurityEvent {
            timestamp: "2026-02-16T10:30:00Z".to_string(),
            event_type: "malicious_url_detected".to_string(),
            severity: "critical".to_string(),
            description: "URL flagged as malicious by VirusTotal".to_string(),
            details: Some("10 vendors flagged this URL".to_string()),
        };

        assert_eq!(event.severity, "critical");
        assert!(event.details.is_some());
    }
}
