<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

class OmegadbInboxes extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function __construct(){
        // Front Forms Actions
        add_action('wp_ajax_omegadb_inboxes_cleaner', array(&$this,'cleaner'));
        add_action('wp_ajax_omegadb_inboxes_form', array(&$this,'ajax'));
        add_action('wp_ajax_nopriv_omegadb_inboxes_form', array(&$this,'ajax'));
        // Actions privates..
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function cleaner(){
        if(is_user_logged_in() && current_user_can('administrator')){
            $type = $this->Disset($_GET,'type');
            $durl = $this->Disset($_SERVER,'HTTP_REFERER');
            $nonc = $this->Disset($_GET,'nonce');
            if(!empty($type) && wp_verify_nonce($nonc,'omegadb_inboxes_cleaner')){
                global $wpdb;
                $wpdb->query("DELETE FROM $wpdb->posts WHERE post_type='$type'");
                $wpdb->query("DELETE FROM $wpdb->postmeta WHERE post_id NOT IN (SELECT id FROM $wpdb->posts)");
            }
            // WP Redirection
            wp_redirect(esc_url($durl),302);
            exit;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    public function ajax(){
        // Set Reponse
        $response = array();
        // Switch Type form
        switch($this->Disset($_REQUEST,'type')) {
            case 'contact':
                $response = $this->insert_contact();
                break;
            case 'report':
                $response = $this->insert_report();
                break;
            default:
                $response = array(
                    'success' => false,
                    'message' => __z('Unknown action')
                );
        }
        // return a reponse
        wp_send_json($response);
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    private function insert_contact(){
        // Set Response
        $response  = array();
        // Set IP Address
        $ipaddress = zeta_client_ipaddress();
        // Verify Google reCAPTCHA
        if(zetaflix_google_recaptcha() === true){
            // Data for firewall
            $firewall = $this->firewall('zetaflix_contact',$ipaddress);
            $oplimits = zeta_get_option('contact_numbers_by_ip');
            // Verify Firewall
            if($firewall < $oplimits || $this->whitelist($ipaddress) === true){
                // verfiy Blacklist
                if($this->blacklist($ipaddress) === false){
                    // Module Options
                    $email = zeta_get_option('contact_email', get_option('admin_email'));
                    $esend = zeta_get_option('contact_notify_email');
                    $sbjct = $this->Disset($_POST,'subject');
                    $mssag = $this->Disset($_POST,'message');
                    $cname = $this->Disset($_POST,'name');
                    $cmail = $this->Disset($_POST,'email');
                    $clink = $this->Disset($_POST,'link');
                    $cckey = md5(time().'contact');
                    // Compose data
                    $post_data = array(
                        'post_type'    => 'zetaflix_contact',
                        'post_status'  => 'unread',
                        'post_author'  => $this->SetUserPost('0'),
                        'post_content' => $mssag,
                        'post_title'   => $sbjct,
                        'post_name'    => $cckey
                    );
                    // Create post ID
                    $post_id = wp_insert_post($post_data);
                    // Verify Error WordPress and Insert Postmeta
                    if(!is_wp_error($post_id)){
                        // Set postmeta
                        $postmeta = array(
                            'ip'    => $ipaddress,
                            'name'  => $cname,
                            'email' => $cmail,
                            'link'  => $clink
                        );
                        // Create postmeta
                        add_post_meta($post_id, 'zetaflix_meta_contact', $postmeta, false);
                        add_post_meta($post_id, 'zetaflix_firewall_inboxes', $ipaddress, false);
                        // Notify by Email
                        if($esend == true){
                            // Compose headers
                            $headers[] = 'Content-Type: text/html; charset=UTF-8';
                            $headers[] = 'Reply-To: '.$cname.' <'.$cmail.'>';
                            // Compose message
                            $message  = '<h2>'.$sbjct.'</h2>';
                            $message .= apply_filters('the_content', $mssag);
                            $message .= '<p><strong>'.__z('From').':</strong> '.$cname.'</p>';
                            $message .= '<p><strong>'.__z('Email Address').':</strong> '.$cmail.'</p>';
                            $message .= '<p><strong>'.__z('Contact Key').':</strong> '.$cckey.'</p>';
                            $message .= '<p><strong>'.__z('IP Address').':</strong>  <a href="https://tools.keycdn.com/geo?host='.$ipaddress.'" target="_blank">'.$ipaddress.'</a></p>';
                            if($clink)
                                $message .= '<p><strong>'.__z('Reference').':</strong> <a href="'.$clink.'" target="_blank">'.$clink.'</a></p>';
                            // Send email
                            wp_mail($email, $this->TextCleaner(__z('New contact message').' : '.$sbjct), $message, $headers);
                        }
                        // Set Response
                        $response = array(
                            'success' => true,
                            'message' => __z('Your message was sent successfully.')
                        );
                    } else {
                        $response = array(
                            'success' => false,
                            'message' => __z('Error WordPress')
                        );
                    }
                }else{
                    $response = array(
                        'success' => false,
                        'message' => __z('Your IP address has been blocked')
                    );
                }
            } else {
                $response = array(
                    'success' => false,
                    'message' => __z('Logs limit exceeded, please try again later.')
                );
            }
        } else {
            $response = array(
                'success' => false,
                'message' => __z('Google reCAPTCHA Error')
            );
        }
        return $response;
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    private function insert_report(){
        // Set Response
        $response = array();
        // Set IP Address
        $ipaddress = zeta_client_ipaddress();
        // Set POST Parent
        $post_parent = $this->Disset($_POST,'postid');
        // Verify Nonce
        if(zetaflix_verify_nonce('zetaflix_report_nonce', $this->Disset($_POST,'nonce')) === true){
            // Data for firewall
            $firewall = $this->firewall('zetaflix_report',$ipaddress);
            $oplimits = zeta_get_option('reports_numbers_by_ip');
            // Verify Firewall
            if($firewall < $oplimits || $this->whitelist($ipaddress) === true){
                // verfiy Blacklist
                if($this->blacklist($ipaddress) === false){
                    // Module Options
                    $email  = zeta_get_option('contact_email', get_option('admin_email'));
                    $esend  = zeta_get_option('report_notify_email');
                    $title  = get_the_title($post_parent);
                    $issues = $this->Disset($_POST,'problem');
                    $mssage = $this->Disset($_POST,'message');
                    $usmail = $this->Disset($_POST,'email');
                    // Compose data
                    $post_data = array(
                        'post_type'    => 'zetaflix_report',
                        'post_status'  => 'unread',
                        'post_parent'  => $post_parent,
                        'post_author'  => $this->SetUserPost('0'),
                        'post_content' => $mssage,
                        'post_title'   => $title,
                        'post_name'    => md5(time().'report')
                    );
                    // Create post ID
                    $post_id = wp_insert_post($post_data);
                    // Verify Error WordPress and Insert Postmeta
                    if(!is_wp_error($post_id)){
                        // Set postmeta
                        $postmeta = array(
                            'ip'       => $ipaddress,
                            'title'    => get_the_title($post_parent),
                            'parent'   => $post_parent,
                            'email'    => $usmail,
                            'problems' => $issues,
                        );
                        // Create postmeta
                        add_post_meta($post_id,'zetaflix_meta_report',$postmeta,false);
                        add_post_meta($post_id, 'zetaflix_firewall_inboxes', $ipaddress, false);
                        // Notify by Email
                        if($esend == true){
                            $subject = __z('New report registered').': '.$title;
                            $message  = '<h2>'.__z('Report details').'</h2>';
                            if(!empty($issues) && is_array($issues)){
                                foreach ($issues as $key => $value) {
                                    $message .= '<li>'.$this->Disset($this->ReportsIssues($value),'title').'</li>';
                                }
                            }
                            if(!empty($mssage)){
                                $message .= '----------------------------';
                                $message .= apply_filters('the_content', $mssage);
                                $message .= '----------------------------';
                            }
                            if(!empty($usmail)){
                                $message .= '<p><strong>'.__z('Sender').':</strong> <a href="mailto:'.$usmail.'">'.$usmail.'</a></p>';
                            }
                            $message .= '<p><strong>'.__z('Permalink').':</strong> '.get_permalink($post_parent).'</p>';
                            $message .= '<p><strong>'.__z('Edit Content').':</strong> '.admin_url('post.php?post='.$post_parent.'&action=edit').'</p>';
                            $message .= '<p><strong>'.__z('IP Address').':</strong> <a href="https://tools.keycdn.com/geo?host='.$ipaddress.'" target="_blank">'.$ipaddress.'</a></p>';
                            wp_mail($email, $this->TextCleaner($subject), $message, array('Content-Type: text/html; charset=UTF-8'));
                        }
                        // Set Response
                        $response = array(
                            'success' => true,
                            'message' => __z('Report sent successfully')
                        );
                    }else{
                        $response = array(
                            'success' => false,
                            'message' => __z('Error WordPress')
                        );
                    }
                } else{
                    $response = array(
                        'success' => false,
                        'message' => __z('Your IP address has been blocked')
                    );
                }
            } else {
                $response = array(
                    'success' => false,
                    'message' => __z('Logs limit exceeded, please try again later.')
                );
            }
        } else {
            $response = array(
                'success' => false,
                'message' => __z('Authentication error')
            );
        }
        return $response;
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    private function firewall($type = '', $ip = ''){
        // Query
        $query_args = array(
            'post_status' => 'unread',
            'post_type'   => $type,
            'meta_query' => array(
                array(
                    'key'   => 'zetaflix_firewall_inboxes',
                    'value' => $ip
                )
            )
        );
        // Run Query
        $query = new WP_Query($query_args);
        // Count
        return $query->found_posts;
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    private function whitelist($ip = ''){
        // Get The List
        $list = zeta_get_option('whitelist');
        // verify whitelist
        if(!empty($list) && in_array($ip, array_column($list,'ip'))){
            return true;
        }else{
            return false;
        }
    }

    /**
     * @since 1.0.0
     * @version 1.1
     */
    private function blacklist($ip = ''){
        // Get The List
        $list = zeta_get_option('blacklist');
        // verify List
        if(!empty($list) && in_array($ip, array_column($list,'ip'))){
            return true;
        }else{
            return false;
        }
    }
}


new OmegadbInboxes;
