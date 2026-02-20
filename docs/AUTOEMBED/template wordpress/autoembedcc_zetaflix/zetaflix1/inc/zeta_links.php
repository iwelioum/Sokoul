<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
**************
* @since 1.0.0
*/

class ZetaLinks{

    // Module Version
    public $version = '2.0';
    // Post Type
    public $typepost = 'zt_links';
    // Post Meta keys
    public $metaurl     = '_zetal_url';
    public $metatype    = '_zetal_type';
    public $metalang    = '_zetal_lang';
    public $metasize    = '_zetal_size';
    public $metaquality = '_zetal_quality';


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        // Init Actions
        add_action('init', array($this,'post_type'), 1 );
        add_action('admin_init', array($this,'add_metabox'), 1);
        // Ajax Actions
        add_action('wp_ajax_zetasave_links', array($this,'admin_post_ajax'));
        add_action('wp_ajax_zetadelt_links', array($this,'admin_delete_ajax'));
        add_action('wp_ajax_zetafrontdeletelink', array($this,'admin_delete_ajax'));
        add_action('wp_ajax_zetareload_links', array($this,'admin_reload_ajax'));
        add_action('wp_ajax_zetaformeditor_links', array($this,'admin_form_editor_ajax'));
        add_action('wp_ajax_zetasaveformeditor_links', array($this,'admin_save_form_editor_ajax'));
        add_action('wp_ajax_zetaupdate_linksdb', array($this,'database'));
        add_action('wp_ajax_zetapostlinks', array($this,'add_frontend'));
        add_action('wp_ajax_zetaformeditor_user', array($this,'front_form_editor_ajax'));
        add_action('wp_ajax_zetasaveformeditor_user', array($this,'front_save_form_editor_ajax'));
        // More actions
        add_action('manage_'.$this->typepost.'_posts_custom_column', array($this,'table_content'), 10, 2 );
        add_action('auth_redirect', array($this,'add_pending'));
        add_action('admin_menu',  array($this,'restore_pending'));
        // Save post actions
        add_action('save_post', array($this, 'save_data_metabox_post_type'));
        // All filters
        add_filter('manage_'.$this->typepost.'_posts_columns', array($this,'table_head'));
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __destruct(){
		return false;
	}


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function types(){
        return array( __z('Download'), __z('Watch online'), __z('Torrent'), __z('Rent or Buy'));
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function langs(){
        return zeta_multiexplode(array(',',', '), zeta_get_option('linkslanguages','English, Spanish, Russian, Italian, Portuguese, Turkish, Bulgarian, Chinese'));
    }


    /**
     * @since 1.0.0
     * @version 1.1
     */
    public static function resolutions(){
        return zeta_multiexplode(array(',',', '), zeta_get_option('linksquality','4k 2160p, HD 1440p, HD 1080p, HD 720p, SD 480p, SD 360p, SD 240p'));
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function post_type(){
        $labels = array(
			'name'           => __z('Links'),
			'singular_name'  => __z('Links'),
			'menu_name'      => __z('Links %%PENDING_COUNT_LINK%%'),
			'name_admin_bar' => __z('Links'),
			'all_items'	     => __z('Links')
		);
		$rewrite = array(
			'slug'       => get_option('zt_links_slug','links'),
			'with_front' => true,
			'pages'      => true,
			'feeds'      => true,
		);
		$args = array(
			'label'               => __z('Links'),
			'description'         => __z('Links manage'),
			'labels'              => $labels,
			'supports'            => array('title','author'),
			'taxonomies'          => array(),
			'hierarchical'        => false,
			'public'              => true,
			'show_ui'             => true,
			'show_in_menu'        => true,
			'menu_position'       => 5,
			'menu_icon'           => 'dashicons-admin-links',
			'show_in_admin_bar'   => true,
			'show_in_nav_menus'   => false,
			'can_export'          => true,
			'has_archive'         => false,
			'exclude_from_search' => true,
			'publicly_queryable'  => true,
			'rewrite'             => $rewrite,
			'capability_type'     => 'post',
		);
        // Register Post type
		register_post_type($this->typepost, $args);
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function database(){
        // Json Header
        header('Content-Type: application/json');
        // POST Data
        $run = zeta_isset($_POST,'run');
        // Ejecutar
        if($run){
            switch ($run) {
                case 'assume':
                    global $wpdb;
                    $xpg = 20;
                    $ttl = $wpdb->get_var("SELECT COUNT(meta_id) FROM $wpdb->postmeta WHERE meta_key = 'zt_postid'");
                    $stp = zeta_isset($_POST,'step');
                    $ttp = ceil($ttl/$xpg);
                    $pct = ($stp * 100 / $ttp);
                    if($stp <= $ttp && $ttp != 0){
                        $this->database_assume($stp,$xpg);
                        $response = array('step'=> $stp,'percentage' => $pct);
                    } else {
                        $response = array('next' => 'update', 'percentage' => 100);
                    }
                    break;

                case 'update':
                    $this->database_update();
                    $response = array('next' => 'cleaner');
                    break;

                case 'cleaner':
                    $this->database_cleaner();
                    update_option('zetaflix_update_linksmodule', true);
                    $response = array('next' => false);
                    break;

                default:
                    $response = array('run' => false);
                    break;
            }
        } else {
            $response = array('run' => false);
        }
        echo json_encode($response);
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function admin_post_ajax(){
        if(!current_user_can('subscriber')){
            $this->add();
            $this->tablelist_admin(zeta_isset($_POST,'postid'));
        }
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function admin_reload_ajax(){
        if(!current_user_can('subscriber')){
            $this->tablelist_admin(zeta_isset($_POST,'id'));
        }
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function admin_delete_ajax(){
        $post_id = zeta_isset($_POST,'id');
        if($post_id && !current_user_can('subscriber')){
            wp_delete_post($post_id);
            return true;
        }
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function admin_form_editor_ajax(){
        $psid = zeta_isset($_POST,'id');
        if($psid && !current_user_can('subscriber')){
            // Postmeta
            $murl = get_post_meta($psid, $this->metaurl, true );
            $typs = get_post_meta($psid, $this->metatype, true );
            $lags = get_post_meta($psid, $this->metalang, true );
            $qual = get_post_meta($psid, $this->metaquality, true );
            $size = get_post_meta($psid, $this->metasize, true );
            $post = get_post($psid);
            $stxt = ($post->post_status != 'publish') ? __z('Save and publish') : __z('Save changes');
            // View
            $out  = '<tr id="zetaeditorlink-'.$psid.'" class="zeta_link_editor fadein">';
            $out .= '<td colspan="10"><div class="zeta_link_form_editor">';
            $out .= '<fieldset>';
            $out .= '<span><select id="zetalinkeditor_type">';
            // Elements defined
            foreach( $this->types() as $type) {
                $out .= '<option '.selected($typs, $type, false).'>'.$type.'</option>';
            }
            $out .= '</select></span>';
            $out .= '<span><select id="zetalinkeditor_lang">';
            // Get item not defined
            if(!in_array($lags, $this->langs())){
                $out .= '<option selected="selected">'.$lags.'</option>';
            }
            // Elements defined
            foreach( $this->langs() as $lang) {
                $out .= '<option '.selected($lags, $lang, false).'>'.$lang.'</option>';
            }
            $out .= '</select></span>';
            $out .= '<span><select id="zetalinkeditor_quality">';
            // Get item not defined
            if(!in_array($qual, $this->resolutions())){
                $out .= '<option selected="selected">'.$qual.'</option>';
            }
            // Elements defined
            foreach( $this->resolutions() as $quality) {
                $out .= '<option '.selected($qual, $quality, false).'>'.$quality.'</option>';
            }
            $out .= '</select></span>';
            $out .= '<span><input id="zetalinkeditor_size" type="text" placeholder="'.__z('File size').'" value="'.$size.'" /></span>';
            $out .= '</fieldset>';
            $out .= '<fieldset><input id="zetalinkeditor_url" class="url" type="text" value="'.$murl.'" /></fieldset>';
            $out .= '<fieldset>';
            $out .= '<a href="#" id="zetalinkeditor_save" class="button button-primary right" data-id="'.$psid.'">'.$stxt.'</a> ';
            $out .= '<a href="#" id="zetalinkeditor_cancel" class="button button-secundary right" data-id="'.$psid.'">'.__z('Cancel').'</a>';
            $out .= '<a href="#" class="button button-secundary zetadelete_links right" data-id="'.$psid.'">'.__z('Delete').'</a>';
            $out .= '</fieldset></div></td></tr>';
            // Compose view
            echo $out;
        }
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function front_form_editor_ajax(){
        if(is_user_logged_in()){
            $post_id = zeta_isset($_POST,'post_id');
            require get_parent_theme_file_path('/inc/parts/links_editor_single.php');
        }
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function front_save_form_editor_ajax(){
        if(is_user_logged_in() && $_SERVER['REQUEST_METHOD'] === 'POST'){
            // Post Data
            $ptid = zeta_isset($_POST,'ptid');
            $lang = zeta_isset($_POST,'lang');
            $type = zeta_isset($_POST,'type');
            $qual = zeta_isset($_POST,'qual');
            $murl = zeta_isset($_POST,'murl');
            $size = zeta_isset($_POST,'size');
            $nonc = zeta_isset($_POST,'nonc');
            $date = date("Y-m-d H:i:s");
            if(wp_verify_nonce($nonc,'zetalinksaveformeditor_'.$ptid)){
                // Compose Post
                $post = array(
                    'ID'                => $ptid,
                    'post_status'       => $this->post_status(),
                    'post_modified'     => $date,
                    'post_modified_gmt' => $date
                );
                // Update Post
                wp_update_post($post);
                // Update Postmeta
                if($lang) update_post_meta($ptid, $this->metalang, esc_attr($lang));
                if($type) update_post_meta($ptid, $this->metatype, esc_attr($type));
                if($qual) update_post_meta($ptid, $this->metaquality, esc_attr($qual));
                if($murl) update_post_meta($ptid, $this->metaurl, esc_attr($murl));
                    else delete_post_meta($ptid, $this->metaurl);
                if($size) update_post_meta($ptid, $this->metasize, esc_attr($size));
                    else delete_post_meta($ptid, $this->metasize);
            }
        }
		echo 'wtf';
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function admin_save_form_editor_ajax(){
        if(!current_user_can('subscriber')){
            $psid = zeta_isset($_POST,'id');
            $murl = zeta_isset($_POST,'url');
            $lags = zeta_isset($_POST,'lang');
            $typs = zeta_isset($_POST,'type');
            $qual = zeta_isset($_POST,'quality');
            $size = zeta_isset($_POST,'size');
            if($psid){
                $post = get_post($psid);
                // update post meta
                if($lags) update_post_meta($psid, $this->metalang, $lags);
                if($typs) update_post_meta($psid, $this->metatype, $typs);
                if($qual) update_post_meta($psid, $this->metaquality, $qual);
                if($murl) update_post_meta($psid, $this->metaurl, $murl);
                    else delete_post_meta($psid, $this->metaurl);
                if($size) update_post_meta($psid, $this->metasize, $size);
                    else delete_post_meta($psid, $this->metasize);
                // Publish Link
                if($post->post_status != 'publish')
                    wp_update_post(array('ID' => $psid, 'post_status' => 'publish'));

                // The view
                $this->table_row($psid);
            }
        }
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function add_metabox(){
        $action = zeta_isset($_GET,'action');
        $option = zeta_get_option('linkshoweditor');
        if($action === 'edit' || $option == true){
            add_meta_box('links-editor', __z('Links'), array($this,'view_metabox'), 'movies', 'normal', 'default');
    		add_meta_box('links-editor', __z('Links'), array($this,'view_metabox'), 'episodes', 'normal', 'default');
            add_meta_box('links-metabox', __z('Links'), array($this,'view_metabox_post_type'), $this->typepost, 'normal', 'high');
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function view_metabox(){
        global $post;
        require get_parent_theme_file_path('/inc/parts/links_editor.php');
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function view_metabox_post_type(){
        global $post;
        require get_parent_theme_file_path('/inc/parts/links_editor_type.php');
        wp_nonce_field('_links_nonce', 'links_nonce');
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function save_data_metabox_post_type($post_id){
        // All verifications
        if( defined('DOING_AUTOSAVE') && DOING_AUTOSAVE ) return;
		if( !isset( $_POST['links_nonce'] ) || ! wp_verify_nonce( $_POST['links_nonce'], '_links_nonce') ) return;
		if( !current_user_can('edit_post', $post_id ) ) return;
        // Post data
        $type = zeta_isset($_POST,'_zetal_type');
        $lang = zeta_isset($_POST,'_zetal_lang');
        $murl = zeta_isset($_POST,'_zetal_url');
        $size = zeta_isset($_POST,'_zetal_size');
        $qual = zeta_isset($_POST,'_zetal_quality');
        // Update post meta
        if($lang) update_post_meta($post_id, $this->metalang, $lang);
        if($type) update_post_meta($post_id, $this->metatype, $type);
        if($qual) update_post_meta($post_id, $this->metaquality, $qual);
        if($murl) update_post_meta($post_id, $this->metaurl, $murl); else delete_post_meta($post_id, $this->metaurl);
        if($size) update_post_meta($post_id, $this->metasize, $size); else delete_post_meta($post_id, $this->metasize);
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function add(){
        $type = zeta_isset($_POST,'type');
        $urls = zeta_isset($_POST,'urls');
        $urls = explode("\n",$urls);
        $numb = count($urls);
        if(!empty($urls)){
            for($n = 0; $n < $numb; $n++){
                $url = isset($urls[$n]) ? $urls[$n] : false;
                if($url){
                    $data = array(
                        'type'    => $type,
                        'url'     => $url,
                        'lang'    => zeta_isset($_POST,'language'),
                        'size'    => zeta_isset($_POST,'size'),
                        'parent'  => zeta_isset($_POST,'postid'),
                        'quality' => zeta_isset($_POST,'quality'),
                    );
                    if($type == __z('Torrent') || filter_var($url,FILTER_VALIDATE_URL)){
                        $this->insert($data);
                    }
                }
            }
        }
        return true;
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function add_frontend(){
        // POST Data
        $data = zeta_isset($_POST,'data');
        $ptid = zeta_isset($_POST,'post_id');
        $noce = zeta_isset($_POST,'nonce');
        $cout = isset($data) ? count($data) : false;
        // Verify nonce
        if(wp_verify_nonce($noce,'zetalinks')){
            // Unserialize data
            for($n = 0; $n < $cout; $n++){
                // Main Data
                $type = isset($data[$n]['type']) ? $data[$n]['type'] : false;
                $link = isset($data[$n]['url']) ? $data[$n]['url'] : false;
                // Compose Link
                $insert = array(
                    'url'     => $link,
                    'type'    => $type,
                    'parent'  => $ptid,
                    'lang'    => isset($data[$n]['lang']) ? $data[$n]['lang'] : false,
                    'size'    => isset($data[$n]['size']) ? $data[$n]['size'] : false,
                    'quality' => isset($data[$n]['quality']) ? $data[$n]['quality'] : false
                );
                // Insert Link
                if($type == __z('Torrent') || filter_var($link,FILTER_VALIDATE_URL)){
                    $this->insert($insert);
                }
            }
            // the Response
            $response = array('response' => true);
        } else {
            $response = array('message' => __z('Invalid verification'));
        }
        echo json_encode( $response );
        // End Action
        wp_die();
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function insert($data = false){
        if(is_array($data)){
            $url     = zeta_isset($data,'url');
            $lang    = zeta_isset($data,'lang');
            $size    = zeta_isset($data,'size');
            $type    = zeta_isset($data,'type');
            $parent  = zeta_isset($data,'parent');
            $quality = zeta_isset($data,'quality');
            if($url && $parent){
                $post = array(
                    'post_title'  => $this->key(),
                    'post_status' => $this->post_status(),
                    'post_type'   => $this->typepost,
                    'post_parent' => $parent,
                    'post_author' => get_current_user_id(),
                );
                $post_id = wp_insert_post($post);
                if($post_id){
                    // Add Post Metas
                    if(filter_var($url, FILTER_VALIDATE_URL)){
                        add_post_meta($post_id, $this->metaurl, esc_url($url), true);
                    } else {
                        add_post_meta($post_id, $this->metaurl, sanitize_text_field($url), true);
                    }
                    if($lang)
                        add_post_meta($post_id, $this->metalang, sanitize_text_field($lang), true);
                    if($size)
                        add_post_meta($post_id, $this->metasize, sanitize_text_field($size), true);
                    if($type)
                        add_post_meta($post_id, $this->metatype, sanitize_text_field($type), true);
                    if($quality)
                        add_post_meta($post_id, $this->metaquality, sanitize_text_field($quality), true);
                    // The return
                    return $post_id;
                }
            }
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function tablelist_front($post_id = false, $typec = false, $box_id = false){
        if(zeta_here_type_links($post_id, $typec) == true){
            global $wpdb;
            $sql  = "SELECT ID, post_author FROM $wpdb->posts ";
            $sql .= "WHERE post_parent = '$post_id' AND post_type = 'zt_links' AND post_status = 'publish' ";
            $sql .= "ORDER BY ID DESC ";
            $results = $wpdb->get_results($sql);
			$linksaccess = zeta_get_option('links_access');		
			$access = ($linksaccess == true) ? (is_user_logged_in()) ? true : false : true;
            if($results){
                $out  = "<div class='link-{$box_id}'>";
                $out .= "<table id='{$box_id}' class='links-table'>";
				$out .= "<thead><tr>";
                $out .= "<th>".__z('Options')."</th>";
                if(zeta_is_true('linksrowshow','qua') == true) $out .= "<th>".__z('Quality')."</th>";
                if(zeta_is_true('linksrowshow','lan') == true) $out .= "<th>".__z('Language')."</th>";
                if(zeta_is_true('linksrowshow','siz') == true) $out .= "<th>".__z('Size')."</th>";
                if(zeta_is_true('linksrowshow','cli') == true) $out .= "<th>".__z('Clicks')."</th>";
                if(zeta_is_true('linksrowshow','add') == true) $out .= "<th>".__z('Added')."</th>";
                if(zeta_is_true('linksrowshow','use') == true) $out .= "<th>".__z('User')."</th>";
                if(is_user_logged_in() && !current_user_can('subscriber'))
                    $out .= "<th>".__z('Manage')."</th>";
                $out .= "</tr></thead>";
				$out .= "<tbody>";
				if($access == true){
					foreach($results as $post){
						$psid = $post->ID;
						$type = get_post_meta($psid, '_zetal_type', true);
						$lang = get_post_meta($psid, '_zetal_lang', true);
						$size = get_post_meta($psid, '_zetal_size', true);
						$murl = get_post_meta($psid, '_zetal_url', true);
						$qual = get_post_meta($psid, '_zetal_quality', true);
						$clik = get_post_meta($psid, 'zt_views_count', true);
						$link = get_permalink($psid);
						$size = ($size) ? $size : '----';
						$clik = ($clik) ? $clik : '0';
						$doma = ($type == __z('Torrent')) ? zeta_compose_domainname('https://utorrent.com') : zeta_compose_domainname($murl);
						$ltxt = ($type == __z('Torrent')) ? __z('Get Torrent') : $typec;
						$date = human_time_diff(get_the_time('U',$psid), current_time('timestamp',$psid));
						$user = get_the_author_meta('nickname',$post->post_author);
						$ulnk = get_author_posts_url($post->post_author);
						$fico = ZETA_GICO.$doma;
						$edit = '<a href="#" class="edit_link" data-id="'.$psid.'">'.__z('Edit').'</a>';
						$delt = '<a href="#" class="delt_link" data-id="'.$psid.'">'.__z('Delete').'</a>';
						if($type == $typec){
							$out .= "<tr id='link-row-{$psid}'>";
							$out .= "<td><div class='link-opt'><img src='{$fico}'><a href='{$link}' target='_blank'>{$ltxt}</a></div></td>";
							if(zeta_is_true('linksrowshow','qua') == true) $out .= "<td><span class='quality'>{$qual}</span></td>";
							if(zeta_is_true('linksrowshow','lan') == true) $out .= "<td><span>{$lang}</span></td>";
							if(zeta_is_true('linksrowshow','siz') == true) $out .= "<td><span>{$size}</span></td>";
							if(zeta_is_true('linksrowshow','cli') == true) $out .= "<td><span>{$clik}</span></td>";
							if(zeta_is_true('linksrowshow','add') == true) $out .= "<td><span>{$date}</span></td>";
							if(zeta_is_true('linksrowshow','use') == true) $out .= "<td><a href='{$ulnk}'>{$user}</a></td>";
							if(is_user_logged_in() && !current_user_can('subscriber'))
								$out .= "<td>{$edit} / {$delt}</td>";
							$out .= "</tr>";
						}
					}
				}else{
					$out .= "<tr id='link-row-hidden'><td><div class='link-opt hidden'>Links are only visible to registered users. <a class='btn-login'>Click to login!</a></div></td></tr>";
				}
                $out .= "</tbody></table></div>";
            }
            echo $out;
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function tablelist_admin($post_id = false){
        global $wpdb;
        // Compose SQL Query
        $sql  = "SELECT ID, post_author, post_status ";
        $sql .= "FROM $wpdb->posts ";
        $sql .= "WHERE post_parent = '$post_id' AND post_type = '$this->typepost' ";
        $sql .= "ORDER BY ID DESC";
        // Get results of SQL
        $results = $wpdb->get_results($sql);
        if(!$results){
            echo '<tr><td colspan="10">'.__z('No link has been added yet').'</td></tr>';
        } else {
            foreach($results as $post){
                $id      = $post->ID;
                $clicks  = get_post_meta($id, 'zt_views_count', true);
                $type    = get_post_meta($id, $this->metatype, true);
                $url     = get_post_meta($id, $this->metaurl, true);
                $lang    = get_post_meta($id, $this->metalang, true);
                $quality = get_post_meta($id, $this->metaquality, true);
                $size    = get_post_meta($id, $this->metasize, true);
                $size    = ($size) ? $size : '----';
                $clicks  = ($clicks) ? $clicks : '0';
                $domain  = ($type == __z('Torrent')) ? __z('uTorrent') : $this->domain($url);
                $user    = get_the_author_meta('nickname', $post->post_author);
                $ulnk    = admin_url('user-edit.php?user_id='.$post->post_author);
                $user    = "<a href='{$ulnk}' target='_blank'>{$user}</a>";
                $added   = human_time_diff(get_the_time('U',$id), current_time('timestamp',$id));
                $edit    = '<a href="#" class="zetaedit_links button button-primary button-small" data-id="'.$id.'">'.__z('Edit').'</a>';
                $delete  = '<a href="#" class="zetadelete_links button button-small" data-id="'.$id.'">'.__z('Delete').'</a>';
                $status  = $post->post_status;
                // Link View
                $out  = "<tr id='link-row-{$id}'>";
                $out .= "<td class='status {$status}'><strong>{$type}</strong></td>";
                $out .= "<td><a href='{$url}' target='_blank'>{$domain}</a></td>";
                $out .= "<td>{$lang}</td>";
                $out .= "<td><strong>{$quality}</strong></td>";
                $out .= "<td>{$size}</td>";
                $out .= "<td>{$clicks}</td>";
                $out .= "<td>{$user}</td>";
                $out .= "<td>{$added}</td>";
                $out .= "<td><span class='managelinks'>{$delete}</span></td>";
                $out .= "<td><span class='managelinks'>{$edit}</span></td>";
                $out .= "</tr>";
                echo $out;
            }
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function table_row($post_id){
        $post = get_post($post_id);
        $clic = get_post_meta($post_id, 'zt_views_count', true);
        $type = get_post_meta($post_id, $this->metatype, true);
        $murl = get_post_meta($post_id, $this->metaurl, true);
        $lang = get_post_meta($post_id, $this->metalang, true);
        $qual = get_post_meta($post_id, $this->metaquality, true);
        $size = get_post_meta($post_id, $this->metasize, true);
        $size = ($size) ? $size : '----';
        $clic = ($clic) ? $clic : '0';
        $dmin = ($type == __z('Torrent')) ? __z('uTorrent') : $this->domain($murl);
        $user = get_the_author_meta('nickname', $post->post_author);
        $ulnk = admin_url('user-edit.php?user_id='.$post->post_author);
        $user = "<a href='{$ulnk}' target='_blank'>{$user}</a>";
        $date = human_time_diff(get_the_time('U',$post_id), current_time('timestamp',$post_id));
        $edit = '<a href="#" class="zetaedit_links button button-primary button-small" data-id="'.$post_id.'">'.__z('Edit').'</a>';
        $delt = '<a href="#" class="zetadelete_links button button-small" data-id="'.$post_id.'">'.__z('Delete').'</a>';
        $stat = $post->post_status;
        // View
        $out  = "<td class='status {$stat}'><strong>{$type}</strong></td>";
        $out .= "<td><a href='{$murl}' target='_blank'>{$dmin}</a></td>";
        $out .= "<td>{$lang}</td>";
        $out .= "<td><strong>{$qual}</strong></td>";
        $out .= "<td>{$size}</td>";
        $out .= "<td>{$clic}</td>";
        $out .= "<td>{$user}</td>";
        $out .= "<td>{$date}</td>";
        $out .= "<td><span class='managelinks'>{$delt}</span></td>";
        $out .= "<td><span class='managelinks'>{$edit}</span></td>";
        echo $out;
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function table_head($defaults){
        $defaults['type']    = __z('Parent');
		$defaults['url']     = __z('Server');
		$defaults['lang']    = __z('Language');
	    $defaults['quality'] = __z('Quality');
		$defaults['clicks']  = __z('Clicks');
	    return $defaults;
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function table_content($column_name, $post_id){
        // Post parent
        $parent_id    = wp_get_post_parent_id($post_id);
        $parent_link  = admin_url('post.php?post='.$parent_id.'&action=edit');
        $parent_title = get_the_title($parent_id);
        // Post Meta
        $link_type    = get_post_meta( $post_id, $this->metatype, true );
        $link_url     = get_post_meta( $post_id, $this->metaurl, true );
        $link_lang    = get_post_meta( $post_id, $this->metalang, true );
        $link_quality = get_post_meta( $post_id, $this->metaquality, true );
        $link_views   = get_post_meta( $post_id, 'zt_views_count', true );
        // Domain link data
        $domain  = ($link_type == __z('Torrent')) ? 'https://utorrent.com' : zeta_compose_domainname($link_url);
        $favicon = ZETA_GICO.$domain;
        $domain  = ($link_type == __z('Torrent')) ? __z('Torrent') : zeta_compose_domainname($link_url);
        // Compose column
        switch($column_name) {
            case 'type':
                $out  = "<a href='{$parent_link}' target='_blank'><strong>{$parent_title}</strong></a>";
                $out .= "<div class='row-actions'>{$link_type}</div>";
                break;
            case 'url':
                $out = "<img src='{$favicon}'/> {$domain}";
                break;
            case 'lang':
                $out = "<span class='dashicons dashicons-translation zeta_links_icon'></span> {$link_lang}";
                break;
            case 'quality':
                $out = "<strong>{$link_quality}</strong>";
                break;
            case 'clicks':
                $out = ($link_views) ? $link_views : '0';
                break;
        }
        // View content
        echo apply_filters('dbmv_link_column_'.$column_name, $out, $post_id );
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function counting_pending($safe_text, $text){
        if(substr_count($text, '%%PENDING_COUNT_LINK%%') ) {
			$text = trim( str_replace('%%PENDING_COUNT_LINK%%', false, $text) );
			$safe_text = esc_attr($text);
			$count = $this->pending_counter();
			if( $count > 0 ){
				$text = esc_attr($text). '<span class="awaiting-mod count-'.$count.'" style="margin-left:7px;"><span class="pending-count">'.$count.'</span></span>';
				return $text;
			}
		}
		return $safe_text;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function pending_counter(){
        $count = get_transient('zetaflix_links_pending');
        if(false === $count){
            // Get pending data
            $count = (int) wp_count_posts($this->typepost,'readable')->pending;
            // Set transient data
            set_transient('zetaflix_links_pending', $count, HOUR_IN_SECONDS);
        }
        return $count;
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function add_pending(){
        add_filter('attribute_escape', array($this,'counting_pending'), 20, 2);
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function restore_pending(){
        remove_filter('attribute_escape', array($this,'counting_pending'), 20, 2);
    }


    /**
     * @since 1.0.0
     * @version 1.1
     */
    public static function shorteners($source = ''){
        $option = zeta_get_option('shorteners');
        if(!empty($option) && is_array($option) && filter_var($source,FILTER_VALIDATE_URL)){
            $numb = array_rand($option);
            $link = $option[$numb]['short'];
            $link = str_replace('{{url}}', $source, $link);
        }else{
            $link = $source;
        }
        return apply_filters('zeta_links_shorteners', $link, $source);
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public static function front_publisher_role(){
        // Post status
        $a = true;
        $b = false;
        // Comparate User Role
        if(current_user_can('administrator')) {
            return zeta_is_true('linksfrontpublishers','adm') ? $a : $b;
        }
        elseif(current_user_can('editor')) {
            return zeta_is_true('linksfrontpublishers','edt') ? $a : $b;
        }
        elseif(current_user_can('author')) {
            return zeta_is_true('linksfrontpublishers','atr') ? $a : $b;
        }
        elseif(current_user_can('contributor')) {
            return zeta_is_true('linksfrontpublishers','ctr') ? $a : $b;
        }
        elseif(current_user_can('subscriber')) {
            return zeta_is_true('linksfrontpublishers','sbr') ? $a : $b;
        }
        else {
            return $b;
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function database_assume($pag = 1, $perpage = 20){
        // Get WPDB library
        global $wpdb;
        // Compose page
        $ppg = $perpage;
        $prp = $pag-1;
        $pgn = $prp*$ppg;
        // Compose SQL Query
        $sql  = "SELECT * FROM $wpdb->postmeta ";
        $sql .= "WHERE meta_key = 'zt_postid' ";
        $sql .= "ORDER BY meta_id DESC LIMIT {$pgn}, {$ppg}";
        // The query
        $results = $wpdb->get_results($sql);
        // Update post_parent
        foreach($results as $meta){
            wp_update_post(array('ID' => $meta->post_id, 'post_parent' => $meta->meta_value));
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function database_update(){
        global $wpdb;
        $wpdb->query("UPDATE $wpdb->postmeta SET meta_key = '{$this->metatype}' WHERE meta_key = 'links_type'");
        $wpdb->query("UPDATE $wpdb->postmeta SET meta_key = '{$this->metaurl}' WHERE meta_key = 'links_url'");
        $wpdb->query("UPDATE $wpdb->postmeta SET meta_key = '{$this->metalang}' WHERE meta_key = 'links_idioma'");
        $wpdb->query("UPDATE $wpdb->postmeta SET meta_key = '{$this->metaquality}' WHERE meta_key = 'links_quality'");
        $wpdb->query("UPDATE $wpdb->postmeta SET meta_key = '{$this->metasize}' WHERE meta_key = 'zt_filesize'");
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function database_cleaner(){
        global $wpdb;
        $wpdb->query("DELETE FROM $wpdb->postmeta WHERE meta_key = 'zt_string'");
        $wpdb->query("DELETE FROM $wpdb->postmeta WHERE meta_key = 'zt_postid'");
        $wpdb->query("DELETE FROM $wpdb->postmeta WHERE meta_key = 'zt_postitle'");
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function post_status(){
        // Post status
        $a = 'publish';
        $b = 'pending';
        // Comparate User Role
        if(current_user_can('administrator')) {
            return zeta_is_true('linkspublishers','adm') ? $a : $b;
        }
        elseif(current_user_can('editor')) {
            return zeta_is_true('linkspublishers','edt') ? $a : $b;
        }
        elseif(current_user_can('author')) {
            return zeta_is_true('linkspublishers','atr') ? $a : $b;
        }
        elseif(current_user_can('contributor')) {
            return zeta_is_true('linkspublishers','ctr') ? $a : $b;
        }
        elseif(current_user_can('subscriber')) {
            return zeta_is_true('linkspublishers','sbr') ? $a : $b;
        }
        else {
            return $b;
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function key(){
        $string  = 'abcdefhiklmnorstuvwxz1234567890ABCDEFGHIJKLMNOPQRSTUVWYZ';
        $comkey  = array();
        $stringL = strlen($string) - 1;
        for ($i = 0; $i < 10; $i++) {
            $n = rand(0, $stringL);
            $comkey[] = $string[$n];
        }
        return implode($comkey);
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function domain($url){
        $htt = array('http://', 'https://', 'ftp://', 'www.');
        $url = explode('/', str_replace($htt, '', $url));
        return zeta_isset($url,0);
    }
}

new ZetaLinks;
