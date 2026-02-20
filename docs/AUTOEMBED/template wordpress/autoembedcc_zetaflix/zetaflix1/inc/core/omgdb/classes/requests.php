<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

class OmegadbRequests extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        add_action('init', array(&$this,'postype'), 0);
        add_action('auth_redirect', array(&$this,'pending_count_filter'));
        add_action('admin_menu',array(&$this,'esc_attr_restore'));
        add_filter('manage_requests_posts_columns', array(&$this,'TableHead'));
        add_action('manage_requests_posts_custom_column', array(&$this,'TableContent'), 10, 2);
        // Admin Control Actions
        add_action('wp_ajax_dbmvrequestcontrol', array(&$this,'AjaxAction'));
        // Public Ajax Actions
        add_action('wp_ajax_omegadb_requests_search', array(&$this,'AjaxSearch'));
        add_action('wp_ajax_omegadb_post_requests', array(&$this,'AjaxPost'));
        add_action('wp_ajax_omegadb_post_archive', array(&$this,'AjaxArchive'));
        // No Private
        add_action('wp_ajax_nopriv_omegadb_requests_search', array(&$this,'AjaxSearch'));
        add_action('wp_ajax_nopriv_omegadb_post_requests', array(&$this,'AjaxPost'));
        add_action('wp_ajax_nopriv_omegadb_post_archive', array(&$this,'AjaxArchive'));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function postype(){
        $labels = array(
    		'name'                => __z('Requests'),
    		'singular_name'       => __z('Requests'),
    		'menu_name'           => is_admin() ? __z('Requests %%PENDING_REQUEST%%') : __z('Requests'),
    		'name_admin_bar'      => __z('Requests'),
    		'all_items'           => __z('Requests'),
    	);
    	$rewrite = array(
    		'slug'                => get_option('zt_requests_slug','requests'),
    		'with_front'          => true,
    		'pages'               => true,
    		'feeds'               => true,
    	);
    	$args = array(
    		'label'               => __z('Requests'),
    		'description'         => __z('Requests manage'),
    		'labels'              => $labels,
    		'supports'            => array('title','thumbnail'),
    		'taxonomies'          => array(),
    		'hierarchical'        => false,
    		'public'              => false,
    		'show_ui'             => true,
    		'show_in_menu'        => true,
    		'menu_position'       => 5,
    		'menu_icon'           => 'dashicons-welcome-add-page',
    		'show_in_admin_bar'   => true,
    		'show_in_nav_menus'   => false,
    		'can_export'          => true,
    		'has_archive'         => true,
    		'exclude_from_search' => true,
    		'publicly_queryable'  => true,
    		'rewrite'             => $rewrite,
    		'capability_type'     => 'post',
    	);
    	register_post_type('requests', $args);
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function remove_esc_attr_and_count($safe_text = '', $text = '') {
		if(substr_count($text,'%%PENDING_REQUEST%%')){
			$text = trim( str_replace('%%PENDING_REQUEST%%', '', $text) );
			remove_filter('attribute_escape', 'remove_esc_attr_and_count', 20, 2);
			$safe_text 	= esc_attr($text);
			$count 		= (int)wp_count_posts('requests','readable')->pending;
			if ( $count > 0 ) {
				$text = esc_attr($text) .'<span class="awaiting-mod count-'.$count.'" style="margin-left:7px;"><span class="pending-count">'.$count.'</span></span>';
				return $text;
			}
		}
		return $safe_text;
	}

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function pending_count_filter() {
		add_filter('attribute_escape', array(&$this,'remove_esc_attr_and_count'), 20, 2);
	}

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function esc_attr_restore() {
		remove_filter('attribute_escape', array(&$this,'remove_esc_attr_and_count'), 20, 2);
	}

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function TableHead($defaults){
        $defaults['tmdb'] = __z('TMDb ID');
	    $defaults['type'] = __z('Type');
		$defaults['ctrl'] = __z('Controls');
	    return $defaults;
	}

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function TableContent($column_name, $post_id){
        $meta = get_post_meta($post_id,'_dbmv_requests_post', true);
        $tmdb = get_post_meta($post_id,'ids', true);
        $type = $this->Disset($meta,'type');
        switch($column_name){
            case 'tmdb':
            echo '<code>'.$tmdb.'</code>';
            break;

            case 'type':
            echo $type == 'movie' ? __z('Movie') : false;
            echo $type == 'tv' ? __z('TV Show') : false;
            break;

            case 'ctrl':
            $out ='';
            if(get_post_status($post_id) !== 'publish'){
                $out .= "<a class='requestscontrol button button-primary tooltip' href='".admin_url("admin-ajax.php?action=dbmvrequestcontrol&dc=act&ref={$post_id}")."' data-post='{$post_id}' data-title='".__z('Add to list of requests')."'>".__z('A')."</a> ";
            }
            // Import and delete
            $out .= "<a class='requestscontrol button tooltip' href='".admin_url("admin-ajax.php?action=dbmvrequestcontrol&dc=iad&ids={$tmdb}&type={$type}&ref={$post_id}")."' data-post='{$post_id}' data-title='".__z('Import content and remove request')."'>".__z('B')."</a> ";
            // Only Import
            $out .= "<a class='requestscontrol button tooltip' href='".admin_url("admin-ajax.php?action=dbmvrequestcontrol&dc=oim&ids={$tmdb}&type={$type}&ref={$post_id}")."' data-post='{$post_id}' data-title='".__z('Import content')."'>".__z('C')."</a> ";
            // Only Delete
            $out .= "<a class='requestscontrol button tooltip' href='".admin_url("admin-ajax.php?action=dbmvrequestcontrol&dc=odl&ref={$post_id}")."' data-post='{$post_id}' data-title='".__z('Remove request')."'>".__z('D')."</a>";
            echo "<span id='request_post_{$post_id}'>".$out."</span>";
            break;
        }
    }

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function AjaxAction(){
        if(is_user_logged_in() && !current_user_can('subscriber')){
            $ctrl = $this->Disset($_GET,'dc');
            $type = $this->Disset($_GET,'type');
            $tmdb = $this->Disset($_GET,'ids');
            $post = $this->Disset($_GET,'ref');
            switch($ctrl) {
                case 'act':
                    $data = array(
                        'ID' => $post,
                        'post_status' => 'publish'
                    );
                    wp_update_post($data);
                    break;
                case 'iad':
                    if($type == 'movie'){
                        new OmegadbImporters('movie', array('id' => $tmdb, 'ed' => false, 'hr' => true));
                    }elseif($type == 'tv'){
                        new OmegadbImporters('tvshow', array('id' => $tmdb, 'ed' => false, 'hr' => true));
                    }
                    wp_delete_post($post);
                    break;

                case 'oim':
                    if($type == 'movie'){
                        new OmegadbImporters('movie', array('id' => $tmdb, 'ed' => false, 'hr' => true));
                    }elseif($type == 'tv'){
                        new OmegadbImporters('tvshow', array('id' => $tmdb, 'ed' => false, 'hr' => true));
                    }
                    break;

                case 'odl':
                    wp_delete_post($post);
                    break;
            }
            wp_redirect(esc_url($this->Disset($_SERVER,'HTTP_REFERER')),302); exit;
        }
    }

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function AjaxSearch(){
        $page	= $this->Disset($_REQUEST,'page');
		$term	= $this->Disset($_REQUEST,'term');
		$type	= $this->Disset($_REQUEST,'type');
		$nonce	= $this->Disset($_REQUEST,'nonce');
		$dateid	= ($type == 'tv') ? 'first_air_date' : 'release_date';
        $mtime  = microtime(TRUE);
        // HTML
        $out_html = '';
        // Verify data
        if(is_user_logged_in() || $this->get_option('requestsunk') == true){
            // Verify Nonce
            if(wp_verify_nonce($nonce, 'omegadb_requests_users')){
                // Api parameters
                $apiarg = array(
                    'api_key'  => $this->get_option('themoviedb',OMEGADB_TMDBKEY),
                    'language' => $this->get_option('language','en-US'),
                    'query'    => $term,
                    'page'     => $page
                );
                // Remote Data
                $tmdb = $this->RemoteJson($apiarg, OMEGADB_TMDBAPI.'/search/'.$type);
                // Total
    			$total_results = $this->Disset($tmdb,'total_results');
    			$total_pages   = $this->Disset($tmdb,'total_pages');
                // Pages
    			$prevpage = ( $page > 1 ) ? $page-1 : false;
    			$nextpage = ( $page < $total_pages) ? $page+1 : false;
                // Results or not Results
                if( $total_results == 0 ) {
    				$out_html .= '<div class="metainfo">'. __z('No results') .'</div>';
    			}
                if($total_results > 1 ) {
    				$out_html .= '<div class="resultinfo"><strong>'.$total_results.'</strong> '. __z('results') .' '. __z('in') .' '.$this->TimeExe($mtime).' '. __z('seconds') .'</div>';
    			}
                // Results
    			$ctd = array();
                $maxwidth = zeta_get_option('max_width','1200');
                $maxwidth = ($maxwidth >= 1400) ? 'full' : 'normal';
    			$results = $this->Disset($tmdb,'results');
    			$out_html .= '<div class="items">';
                if($results){
                    foreach($results as $ci) {
        				$ctd_id		= $ctd[] = $ci['id'];
        				$ctd_title	= $ctd[] = ( $type == 'tv' ) ? $ci['name'] : $ci['title'];
        				$ctd_poster	= $ctd[] = $ci['poster_path'];
        				$ctd_date	= $ctd[] = ( $ci[$dateid] ) ? $ci[$dateid] : '--';
        				$img		= ( $ctd_poster ) ? 'https://image.tmdb.org/t/p/w185'.$ctd_poster : ZETA_URI.'/assets/img/no/zt_poster.png';
        				// Verificar contenido repetido
        				if($type == 'tv') {
                            $check = $this->VeryTMDb('ids',$ctd_id,array('tvshows','requests'));
        				}elseif($type == 'movie') {
                            $check = $this->VeryTMDb('idtmdb',$ctd_id,array('movies','requests'));
        				}

        				$exclude = ($check) ? ' existing' : 'get_data';
        				$import	 = (!$check) ? '<span class="itm-request">'.__z('Request').'</span><a class="get_content_omegadb" data-id="'.$ctd_id.'" data-type="'.$type.'" data-nonce="'.wp_create_nonce($ctd_id.'_post_request').'">'. __z('Request') .'</a>' : '<a href="'.get_permalink($check).'" class="itm-exists">'. __z('Already exists').'</span>';
					
						$existing = ($check) ? 'href="'.get_permalink($check).'"' : null;
						$class = ($check) ? ' exstng' : null;
						$tmdburl = (!$existing && $ctd_id && $type) ? 'href="'.OMEGADB_TMDBURL.'/'.$type.'/'.$ctd_id.'" rel="nofollow" target="_blank" ' : null;
						$urltitle = ($existing) ? __z('Watch').' '.$ctd_title :  __z('Read on TMDB');
						
						$out_html .= '<div id="item-'.$ctd_id.'" class="display-item'.$class.'">';						
						$out_html .= '<div class="item-box">';
						$out_html .= '<a '.$tmdburl.$existing.' class="ml-mask jt" data-hasqtip="112" title="'.$ctd_title.'"></a>';
						$out_html .= '<img data-original="'. $img .'" class="thumb mli-thumb" alt="'. $ctd_title .'" src="'. $img .'">';
						$out_html .= '<div class="item-desc">';
						$out_html .= '<div class="item-desc-title">';
						$out_html .= '<h3>'.$ctd_title.'</h3>';
						$out_html .= '</div>';
						$out_html .= '</div>';
						$out_html .= '</div>';
						$out_html .= '<div class="info-box">'.$import.'</div>';
						$out_html .= '</div>';
						
						
        				//$out_html .= '<article id="'.$ctd_id.'" class="item animation-1 '.$exclude.'">';
        				//$out_html .= '<div class="box">';
        				//$out_html .= '<div class="poster"><img src="'. $img .'" /></div>';
        				//$out_html .= '<h3>'. $ctd_title .'</h3>';
        				//$out_html .= '<div class="data"><span id="tmdb-'.$ctd_id.'">'.$import.'</span></div>';
        				//$out_html .= '</div>';
        				//$out_html .= '</article>';

        			}
                }
    			$out_html .= '</div>';
            } else {
                $out_html .= '<div class="metainfo">'.__z('Error verification nonce').'</div>';
            }

        }else{
            $out_html .= '<div class="metainfo"><div class="login-msg">'.__z('Please <a class="clicklogin">sign in</a> to continue').'</div></div>';
        }
        // Compose View HTML
        echo apply_filters('omegadb_requests_results', $out_html, $term.$page);
        // Die Action
        wp_die();
    }

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function AjaxArchive(){
        $pids = $this->Disset($_REQUEST,'id');
        $html = '';
        if($pids){
            // Main Data
            $meta = get_post_meta($pids,'_dbmv_requests_post', true);
            $titl = get_the_title($pids);
            // Post Data
            $post_type = $this->Disset($meta,'type');
            $post_over = $this->Disset($meta,'overview');
            $post_badr = $this->Disset($meta,'backdrop');
            // Set Type Content
            if( $post_type == 'movie') $maintype = __z('Movie');
			if( $post_type == 'tv') $maintype = __z('TVShow');
            // HTML
            $html .= ($post_badr) ? "<div class='backdrop'><img src='https:\/\/image.tmdb.org/t/p/w500{$post_badr}'> <span>{$maintype}</span></div>" : null;
            $html .= "<div class='data'>";
			$html .= "<h3>{$titl}</h3>";
			$html .= ($post_over) ? "<p>{$post_over}</p>" : null;
			$html .= "</div>";
        }
        // HTML Viewer
        echo apply_filters('requests_archive_post', $html, $pids);
        // Die Action
        wp_die();
    }

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function AjaxPost(){
        $tmid = $this->Disset($_REQUEST,'id');
        $type = $this->Disset($_REQUEST,'type');
        $nonc = $this->Disset($_REQUEST,'nonce');
        // Verify data
        if(is_user_logged_in() || $this->get_option('requestsunk') == true){
            if(wp_verify_nonce($nonc, $tmid.'_post_request')){
                // Set Post Status
                $post_status = $this->RPostStatus();
                // Args
                $apiarg = array(
                    'api_key' => $this->get_option('themoviedb',OMEGADB_TMDBKEY),
                    'language' => $this->get_option('language','en-US'),
                    'include_image_language' => $this->get_option('language','en-US').',null',
                );
                $tmdb = $this->RemoteJson($apiarg, OMEGADB_TMDBAPI.'/'.$type.'/'.$tmid);
                // Remote Json data
                $tmdb_overview = $this->Disset($tmdb,'overview');
                $tmdb_namtitle = ($type == 'tv') ? $this->Disset($tmdb,'name') : $this->Disset($tmdb,'title');
                $tmdb_backdrop = $this->Disset($tmdb,'backdrop_path');
                $tmdb_imposter = $this->Disset($tmdb,'poster_path');
                // Compose Entry
                $post_data = array(
                    'post_title'	=> $this->TextCleaner($tmdb_namtitle),
    				'post_status'	=> $post_status,
    				'post_type'		=> 'requests',
    				'post_date'     => date('Y-m-d H:i:s'),
    				'post_date_gmt' => date('Y-m-d H:i:s'),
    				'post_author'	=> is_user_logged_in() ? get_current_user_id() : '1'
                );
                // Verify Title
                if($tmdb_namtitle && !$this->VeryTMDb('ids',$tmid,'requests')){
                    // Admin Email
                    $email = $this->get_option('request-email');
                    // Insert post
    				$post_id = wp_insert_post($post_data);
					
    				// Post Meta
    				$meta_data = array(
    					'type'		=> $type,
    					'overview'	=> $tmdb_overview,
    					'backdrop'	=> $tmdb_backdrop,
    					'poster'	=> $tmdb_imposter,
    				);
					//$id = ($type == 'movie') ? 'idtmdb' : 'ids';
    				add_post_meta($post_id, 'ids', esc_attr($tmid));
    				add_post_meta($post_id,'_dbmv_requests_post', $meta_data);
                    // Email notification
                    if(isset($email) && is_email($email)){
                        $subject = sprintf( __z('New request: %s'), $tmdb_namtitle);
                        $message = $this->NotifyMessage($post_status,$tmdb_namtitle);
                        $headers = array('Content-Type: text/html; charset=UTF-8');
                        wp_mail($email, $subject, $message, $headers);
                    }
                }
            }
        }
        // Die Action
        wp_die();
    }

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function NotifyMessage($status = 'publish', $title = ''){
        switch($status) {
            case 'publish':
                $message = sprintf( __z('The title %s has been added to the list of requests correctly'), '<strong>'.$title.'</strong>');
            break;

            case 'pending':
                $message = sprintf( __z('The title %s has been suggested to be added to the list of requests, enter wp-admin to verify it.'), '<strong>'.$title.'</strong>');
            break;
        }

        return '<p>'.$message.'</p>';
    }

    /**
    * @since 1.0.0
    * @version 1.0
    */
    public function RPostStatus(){
        // Post status
        $a = 'publish';
        $b = 'pending';
        // Comparate User Role
        if(!is_user_logged_in()){
            return $this->get_option('reqauto-unk') ? $a : $b;
        }
        elseif(current_user_can('administrator')) {
            return $this->get_option('reqauto-adm') ? $a : $b;
        }
        elseif(current_user_can('editor')) {
            return $this->get_option('reqauto-edi') ? $a : $b;
        }
        elseif(current_user_can('author')) {
            return $this->get_option('reqauto-aut') ? $a : $b;
        }
        elseif(current_user_can('contributor')) {
            return $this->get_option('reqauto-con') ? $a : $b;
        }
        elseif(current_user_can('subscriber')) {
            return $this->get_option('reqauto-sub') ? $a : $b;
        }
        else {
            return $b;
        }
    }
}

new OmegadbRequests;
