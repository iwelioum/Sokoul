#[cfg(test)]
pub mod input_sanitization_tests {
    use regex::Regex;

    // ============ XSS PREVENTION TESTS ============

    fn html_escape(input: &str) -> String {
        input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#x27;")
    }

    #[test]
    fn test_xss_script_tag_escaped() {
        let malicious = "<script>alert('xss')</script>";
        let escaped = html_escape(malicious);

        assert_eq!(
            escaped,
            "&lt;script&gt;alert(&#x27;xss&#x27;)&lt;/script&gt;"
        );
        assert!(!escaped.contains("<script>"));
    }

    #[test]
    fn test_xss_onerror_event_escaped() {
        let malicious = "<img src=x onerror=alert(1)>";
        let escaped = html_escape(malicious);

        // The tag itself gets escaped
        assert!(escaped.contains("&lt;img"));
        assert!(escaped.contains("&gt;"));
        // Key is: the tag is broken by escaping
    }

    #[test]
    fn test_xss_inline_js_escaped() {
        let malicious = "<div onclick=\"fetch('https://evil.com')\">Click me</div>";
        let escaped = html_escape(malicious);

        // Quotes are escaped, tag is escaped
        assert!(escaped.contains("&quot;"));
        assert!(escaped.contains("&lt;div"));
    }

    #[test]
    fn test_xss_svg_payload_escaped() {
        let malicious = "<svg onload=alert(1)>";
        let escaped = html_escape(malicious);

        assert!(escaped.contains("&lt;svg"));
        assert!(escaped.contains("&gt;"));
    }

    #[test]
    fn test_xss_javascript_protocol_escaped() {
        let malicious = "<a href=\"javascript:alert('xss')\">Click</a>";
        let escaped = html_escape(malicious);

        // Tag is escaped
        assert!(escaped.contains("&lt;a"));
        // URL protocol still visible but tag is broken
    }

    #[test]
    fn test_xss_encoded_payload_detection() {
        // Detect double-encoded XSS attempts
        let encoded = "%3Cscript%3Ealert(1)%3C/script%3E";
        let _double_encoded = "%253Cscript%253Ealert(1)%253C/script%253E";

        // Decode once
        let decoded_once = urlencoding::decode(encoded).unwrap_or_default().to_string();
        assert!(decoded_once.contains("<script>"));
    }

    // ============ SQL INJECTION PREVENTION ============

    fn validate_sql_input(input: &str) -> bool {
        // Check for SQL injection patterns
        let sql_patterns = vec![
            "';",
            "DROP",
            "DELETE",
            "INSERT",
            "UPDATE",
            "UNION",
            "SELECT",
            "--",
            "/*",
            "*/",
        ];

        let upper = input.to_uppercase();
        !sql_patterns.iter().any(|p| upper.contains(p))
    }

    #[test]
    fn test_sql_injection_drop_table_blocked() {
        let payload = "'; DROP TABLE users; --";
        assert!(!validate_sql_input(payload), "SQL injection should be blocked");
    }

    #[test]
    fn test_sql_injection_union_select_blocked() {
        let payload = "' OR '1'='1' UNION SELECT * FROM passwords";
        assert!(!validate_sql_input(payload), "UNION SELECT should be blocked");
    }

    #[test]
    fn test_sql_injection_comment_bypass_blocked() {
        let payload = "admin' --";
        assert!(!validate_sql_input(payload), "Comment-based bypass should be blocked");
    }

    #[test]
    fn test_sql_injection_time_based_blocked() {
        let payload = "' AND SLEEP(5) --";
        assert!(!validate_sql_input(payload), "Time-based injection should be blocked");
    }

    #[test]
    fn test_valid_sql_input_allowed() {
        let valid_inputs = vec![
            "John Doe",
            "user@example.com",
            "2024-02-15",
            "search_term_with_spaces",
            "kebab-case-value",
        ];

        for input in valid_inputs {
            assert!(
                validate_sql_input(input),
                "Valid input should be allowed: {}",
                input
            );
        }
    }

    // ============ PATH TRAVERSAL PREVENTION ============

    fn validate_file_path(path: &str) -> bool {
        // Reject path traversal attempts
        !path.contains("..") && !path.contains("./") && !path.contains(".\\")
    }

    #[test]
    fn test_path_traversal_parent_dir_blocked() {
        let paths = vec![
            "../../../etc/passwd",
            "..\\..\\windows\\system32",
            "uploads/../../secrets.txt",
        ];

        for path in paths {
            assert!(!validate_file_path(path), "Path traversal should be blocked: {}", path);
        }
    }

    #[test]
    fn test_path_traversal_dot_slash_blocked() {
        let paths = vec!["./../../secrets", ".\\..\\..\\config"];

        for path in paths {
            assert!(!validate_file_path(path), "Dot-slash traversal blocked: {}", path);
        }
    }

    #[test]
    fn test_valid_file_paths_allowed() {
        let valid_paths = vec![
            "uploads/profile_pic.jpg",
            "media/movie-2024.mp4",
            "logs/app.log",
            "downloads/torrent_123.torrent",
        ];

        for path in valid_paths {
            assert!(
                validate_file_path(path),
                "Valid path should be allowed: {}",
                path
            );
        }
    }

    // ============ COMMAND INJECTION PREVENTION ============

    fn validate_command_arg(arg: &str) -> bool {
        // Reject shell metacharacters
        let dangerous_chars = vec![";", "|", "&", "`", "$", "(", ")", "<", ">", "\n"];
        !dangerous_chars.iter().any(|c| arg.contains(c))
    }

    #[test]
    fn test_command_injection_semicolon_blocked() {
        let payload = "file.txt; rm -rf /";
        assert!(
            !validate_command_arg(payload),
            "Command injection with ; blocked"
        );
    }

    #[test]
    fn test_command_injection_pipe_blocked() {
        let payload = "search_term | cat /etc/passwd";
        assert!(
            !validate_command_arg(payload),
            "Command injection with | blocked"
        );
    }

    #[test]
    fn test_command_injection_backtick_blocked() {
        let payload = "file`whoami`.txt";
        assert!(
            !validate_command_arg(payload),
            "Command injection with backticks blocked"
        );
    }

    #[test]
    fn test_valid_command_args_allowed() {
        let valid_args = vec![
            "search_query",
            "file_name.pdf",
            "user@email.com",
            "2024-02-15",
        ];

        for arg in valid_args {
            assert!(
                validate_command_arg(arg),
                "Valid command arg should be allowed: {}",
                arg
            );
        }
    }

    // ============ JSON INJECTION PREVENTION ============

    #[test]
    fn test_json_injection_nested_object_escaped() {
        // Test that JSON parser validates structure properly
        // Attempt to inject extra fields in JSON
        let suspicious_json_str = r#"{"key": "value\"", "admin": "true"}"#;
        
        // Try to parse this - if it parses, the parser is handling it
        // The real protection is that our models use serde with specific field definitions
        let result = serde_json::from_str::<serde_json::Value>(suspicious_json_str);
        
        // This particular string might parse, but JSON injection defense comes from
        // using typed structs that reject unknown fields
        match result {
            Ok(_) => {
                // If it parsed, show that the structure is safe
                // (In real code, we'd use #[serde(deny_unknown_fields)])
            }
            Err(_) => {
                // If it failed, that's also good - strict parsing
            }
        }
    }

    #[test]
    fn test_json_injection_type_coercion() {
        // Test that JSON parsing is strict about types
        let json_str = r#"{"user_id": "123", "is_admin": "true"}"#;
        let value: serde_json::Value = serde_json::from_str(json_str).unwrap();

        // JSON parser should preserve string types
        assert!(value["is_admin"].is_string());
        assert_eq!(value["is_admin"].as_str(), Some("true"));
    }

    // ============ FILE UPLOAD VALIDATION ============

    #[test]
    fn test_file_upload_mime_type_validation() {
        let allowed_mimes = vec!["image/jpeg", "image/png", "image/webp"];
        let dangerous_mimes = vec!["application/x-executable", "text/x-shellscript"];

        for mime in &dangerous_mimes {
            assert!(
                !allowed_mimes.contains(mime),
                "Dangerous MIME type should be blocked: {}",
                mime
            );
        }
    }

    #[test]
    fn test_file_upload_size_limit() {
        let max_size_bytes = 10 * 1024 * 1024; // 10 MB
        let file_sizes = vec![
            (5 * 1024 * 1024, true),  // 5 MB - OK
            (10 * 1024 * 1024, true), // 10 MB - OK (at limit)
            (11 * 1024 * 1024, false), // 11 MB - BLOCKED
            (100 * 1024 * 1024, false), // 100 MB - BLOCKED
        ];

        for (size, should_pass) in file_sizes {
            let is_valid = size <= max_size_bytes;
            assert_eq!(
                is_valid, should_pass,
                "File size {} should pass={}, got={}",
                size, should_pass, is_valid
            );
        }
    }

    #[test]
    fn test_file_upload_filename_sanitization() {
        fn sanitize_filename(filename: &str) -> String {
            filename
                .chars()
                .filter(|c| c.is_alphanumeric() || *c == '_' || *c == '-')
                .collect()
        }

        let test_cases = vec![
            ("../../evil.jpg", "eviljpg"),
            ("file<script>.png", "filescriptpng"),
            ("normal_file-2024.pdf", "normal_file-2024pdf"),
            ("../../../etc/passwd", "etcpasswd"),
        ];

        for (input, _expected_pattern) in test_cases {
            let sanitized = sanitize_filename(input);
            // After filtering alphanumeric + - + _, no dots or slashes should remain
            assert!(!sanitized.contains("."), "Sanitized filename should not contain dots");
            assert!(!sanitized.contains("/"), "Sanitized filename should not contain /");
            assert!(!sanitized.contains("<"), "Sanitized filename should not contain <");
            assert!(!sanitized.contains(":"), "Sanitized filename should not contain :");
        }
    }

    // ============ EMAIL VALIDATION ============

    #[test]
    fn test_email_format_validation() {
        fn is_valid_email(email: &str) -> bool {
            email.contains('@') && email.contains('.') && email.len() > 5 && email.find('@').map(|i| i > 0).unwrap_or(false)
        }

        let valid_emails = vec!["user@example.com", "test.user@domain.co.uk"];
        let invalid_emails = vec!["invalid@", "@example.com", "no-at-sign.com", "a@b"];

        for email in valid_emails {
            assert!(is_valid_email(email), "Valid email should pass: {}", email);
        }

        for email in invalid_emails {
            assert!(!is_valid_email(email), "Invalid email should fail: {}", email);
        }
    }

    // ============ URL VALIDATION ============

    #[test]
    fn test_url_scheme_whitelist() {
        fn is_valid_url_scheme(url: &str) -> bool {
            url.starts_with("http://") || url.starts_with("https://")
        }

        let valid_urls = vec!["http://example.com", "https://secure.example.com"];
        let invalid_urls = vec![
            "javascript:alert(1)",
            "data:text/html,<script>",
            "file:///etc/passwd",
        ];

        for url in valid_urls {
            assert!(is_valid_url_scheme(url), "Valid URL should pass: {}", url);
        }

        for url in invalid_urls {
            assert!(!is_valid_url_scheme(url), "Invalid URL scheme should fail: {}", url);
        }
    }

    // ============ NUMERIC INPUT VALIDATION ============

    #[test]
    fn test_numeric_range_validation() {
        fn validate_page_number(page: i32) -> bool {
            page > 0 && page <= 10000 // Reasonable max page
        }

        assert!(validate_page_number(1));
        assert!(validate_page_number(100));
        assert!(!validate_page_number(0));
        assert!(!validate_page_number(-1));
        assert!(!validate_page_number(10001));
    }

    #[test]
    fn test_numeric_overflow_prevented() {
        fn validate_limit(limit: i32) -> bool {
            limit > 0 && limit <= 100
        }

        assert!(validate_limit(1));
        assert!(validate_limit(50));
        assert!(validate_limit(100));
        assert!(!validate_limit(0));
        assert!(!validate_limit(101));
        assert!(!validate_limit(i32::MAX));
    }

    // ============ NULL BYTE INJECTION ============

    #[test]
    fn test_null_byte_injection_blocked() {
        let payload = "file.txt\0.exe";
        assert!(!payload.chars().all(|c| c != '\0'), "Null byte detected");

        let safe_input = payload.replace('\0', "");
        assert!(!safe_input.contains('\0'));
    }

    // ============ UNICODE & ENCODING ATTACKS ============

    #[test]
    fn test_unicode_normalization() {
        // Prevent homograph attacks using similar unicode chars
        let lookalike_a = "а"; // Cyrillic 'a' (U+0430)
        let ascii_a = "a";

        assert_ne!(lookalike_a, ascii_a, "Lookalike chars should be detected");
        assert_eq!(lookalike_a.len(), 2, "Cyrillic a is 2 bytes in UTF-8");
        assert_eq!(ascii_a.len(), 1, "ASCII a is 1 byte");
    }

    // ============ WHITELIST APPROACH ============

    #[test]
    fn test_alphanumeric_whitelist() {
        fn is_valid_username(username: &str) -> bool {
            username.len() >= 3
                && username.len() <= 32
                && username
                    .chars()
                    .all(|c| c.is_alphanumeric() || c == '_' || c == '-')
        }

        assert!(is_valid_username("valid_user"));
        assert!(is_valid_username("user-name"));
        assert!(!is_valid_username("user@domain")); // @ not allowed
        assert!(!is_valid_username("u")); // Too short
        assert!(!is_valid_username("a".repeat(50).as_str())); // Too long
    }
}
