<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/


class OmegadbDashboard extends OmegadbHelpers{


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        add_action('wp_dashboard_setup', array($this,'widget'));
        add_action('wp_ajax_omegadb_inboxes', array($this,'ajax_inbox'));
        add_action('wp_ajax_omegadb_inboxes_reading', array($this,'ajax_inbox_reading'));
        add_action('wp_ajax_omegadb_inboxes_deleting', array($this,'ajax_inbox_deleting'));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function widget(){
        wp_add_dashboard_widget('zetaflix_dashboard_widget', __z('Zetaflix'), array($this,'view'));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function view(){
        $nonce = wp_create_nonce('omegadb_inboxes_cleaner');
        require get_parent_theme_file_path('/inc/core/omgdb/tpl/dashboard_widget.php');
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function ajax_inbox_deleting(){
        $post = $this->Disset($_POST,'post');
        $type = $this->Disset($_POST,'type');
        $nonc = $this->Disset($_POST,'nonce');
        $resp = array();
        if(wp_verify_nonce($nonc,'omegadb-inboxes-nonce')){
            // delete post
            if(wp_delete_post($post)){
                // stats
                $post_unread = $this->total_by_status('zetaflix_'.$type,'unread');
                $post_read   = $this->total_by_status('zetaflix_'.$type,'read');
                $post_total  = ($post_unread+$post_read);
                $resp = array(
                    'success' => true,
                    'unread'  => $post_unread,
                    'read'    => $post_read,
                    'total'   => $post_total
                );
            }else{
                $resp = array(
                    'success' => false
                );
            }
        }else{
            $resp = array(
                'success' => false
            );
        }
        // Json Response
        wp_send_json($resp);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function ajax_inbox_reading(){
        $type = $this->Disset($_POST,'type');
        $post = $this->Disset($_POST,'post');
        $nonc = $this->Disset($_POST,'nonce');
        $resp = array();
        if(wp_verify_nonce($nonc,'omegadb-inboxes-nonce')){
            switch ($type) {
                case 'report':
                    $resp = $this->read_report($post);
                    break;
                case 'contact':
                    $resp = $this->read_contact($post);
                    break;
            }
        }else{
            $resp = __z('Authentication error');
        }
        // Response
        wp_die($resp);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function ajax_inbox(){
        // Post Data
        $type = $this->Disset($_REQUEST,'type');
        $page = $this->Disset($_REQUEST,'page');
        $nonc = $this->Disset($_REQUEST,'nonce');
        $resp = array();
        if(wp_verify_nonce($nonc,'omegadb-inboxes-nonce')){
            switch ($type) {
                case 'report':
                    $resp = $this->get_reports($page);
                    break;
                case 'contact':
                    $resp = $this->get_contact($page);
                    break;
            }
        }else{
            $resp = array(
                'error' => __z('Authentication error')
            );
        }
        // Json Response
        wp_send_json($resp);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function read_report($post_id){
        // post data
        $stus = get_post_status($post_id);
        $meta = get_post_meta($post_id, 'zetaflix_meta_report', true);
        $cont = apply_filters('the_content', get_post_field('post_content', $post_id));
        $usip = $this->Disset($meta,'ip');
        $prms = $this->Disset($meta,'problems');
        $user = $this->Disset($meta,'email');
        $prnt = $this->Disset($meta,'parent');
        $user = $user ? '<a href="mailto:'.$user.'">'.$user.'</a>' : __z('Guest');
        // Update post status
        if($stus == 'unread'){
            wp_update_post(array('ID' => $post_id,'post_status' => 'read'));
            delete_post_meta($post_id,'zetaflix_firewall_inboxes');
        }
        // Compose HTML Out
        $out_html = '<h3>'.__z('Report details').'</h3>';
        if($prms && is_array($prms)){
            $out_html .= '<ul class="problems">';
            foreach ($this->Disset($meta,'problems') as $key => $value) {
                $out_html .= '<li>'.$this->Disset($this->ReportsIssues($value),'title').'</li>';
            }
            $out_html .= '</ul>';
        }
        $out_html .= '<div class="text-content">'.$cont.'</div>';
        $out_html .= '<p><strong>'.__z('Sender').':</strong> '.$user.'</p>';
        $out_html .= '<p><strong>'.__z('IP Address').':</strong> <a href="https://tools.keycdn.com/geo?host='.$usip.'" target="_blank">'.$usip.'</a></p>';
        $out_html .= '<div class="controls">';
        $out_html .= '<a href="'.admin_url('post.php?action=edit&post='.$prnt).'" target="_blank" class="button button-primary button-small">'.__z('Edit').'</a>';
        $out_html .= '<a href="'.get_permalink($prnt).'" target="_blank" class="button button-small">'.__z('View Post').'</a>';
        $out_html .= '<a href="#" class="button button-small close-inboxes" data-type="report" data-id="'.$post_id.'">'.__z('Close').'</a>';
        $out_html .= '</div>';
        // return HTML
        return $out_html;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function read_contact($post_id){
        // post data
        $stus = get_post_status($post_id);
        $sbjt = get_the_title($post_id);
        $meta = get_post_meta($post_id, 'zetaflix_meta_contact', true);
        $cont = apply_filters('the_content', get_post_field('post_content', $post_id));
        $usip = $this->Disset($meta,'ip');
        $link = $this->Disset($meta,'link');
        $name = $this->Disset($meta,'name');
        $emal = $this->Disset($meta,'email');
        $clnk = 'mailto:'.$emal.'?subject='.__z('Reply').': '.$sbjt;
        // Update post status
        if($stus == 'unread'){
            wp_update_post(array('ID' => $post_id,'post_status' => 'read'));
            delete_post_meta($post_id,'zetaflix_firewall_inboxes');
        }
        // Compose HTML Out
        $out_html = '<h3>'.$sbjt.'</h3>';
        $out_html .= '<p><strong>'.__z('From').':</strong> '.$name.'</p>';
        $out_html .= '<p><strong>'.__z('Email Address').':</strong> '.'<a href="'.$clnk.'">'.$emal.'</a>'.'</p>';
        $out_html .= '<div class="text-content">'.$cont.'</div>';
        if($link)
            $out_html .= '<p class="fixed"><strong>'.__z('Reference').':</strong> <a href="'.$link.'" target="_blank">'.$link.'</a></p>';
        $out_html .= '<p><strong>'.__z('IP Address').':</strong> <a href="https://tools.keycdn.com/geo?host='.$usip.'" target="_blank">'.$usip.'</a></p>';
        $out_html .= '<div class="controls">';
        $out_html .= '<a href="'.$clnk.'" class="button button-primary button-small">'.__z('Answer').'</a>';
        $out_html .= '<a href="#" class="button button-small close-inboxes" data-type="contact" data-id="'.$post_id.'">'.__z('Close').'</a>';
        $out_html .= '</div>';
        // return HTML
        return $out_html;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function get_reports($page = 1){
        // Data
        $post_type   = 'zetaflix_report';
        $post_unread = $this->total_by_status($post_type,'unread');
        $post_read   = $this->total_by_status($post_type,'read');
        $post_total  = ($post_unread+$post_read);
        // Pre Response
        $response = array();
        // Compose Response
        $response = array(
            'total'  => $post_total,
            'pages'  => ceil($post_total/10),
            'unread' => $post_unread,
            'read'   => $post_read,
            'posts'  => $this->get_posts($post_type, $page)
        );
        // The response
        return $response;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function get_contact($page = 1){
        // Data
        $post_type   = 'zetaflix_contact';
        $post_unread = $this->total_by_status($post_type,'unread');
        $post_read   = $this->total_by_status($post_type,'read');
        $post_total  = ($post_unread+$post_read);
        // Pre Response
        $response = array();
        // Compose Response
        $response = array(
            'total'  => $post_total,
            'pages'  => ceil($post_total/10),
            'unread' => $post_unread,
            'read'   => $post_read,
            'posts'  => $this->get_posts($post_type, $page)
        );
        // The response
        return $response;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function total_by_status($type, $status){
        global $wpdb;
        $query = $wpdb->prepare("SELECT COUNT(*) FROM {$wpdb->posts} WHERE post_type = %s AND post_status = %s", $type, $status);
        return $wpdb->get_var($query);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function total_by_type($type){
        global $wpdb;
        $query = $wpdb->prepare("SELECT COUNT(*) FROM {$wpdb->posts} WHERE post_type = %s", $type);
        return $wpdb->get_var($query);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function get_posts($post_type, $page){
        global $wpdb;
        $query = $wpdb->prepare("SELECT ID, post_title, post_date, post_status FROM {$wpdb->posts} WHERE post_type = %s ORDER BY ID DESC LIMIT 10 OFFSET ".($page-1)*10, $post_type);
        $query = $wpdb->get_results($query,'ARRAY_A');
        return $query;
    }

}

// The Class
new OmegadbDashboard;
