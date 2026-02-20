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

class ZetaDatabase{

    public $version = '2.1';
    public $options = '';


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        if(is_admin()){
            // Ajax Actions
            add_action('wp_ajax_zetaflixgeneratewidgets', array($this,'insert_widgets_ajax'));
            add_action('wp_ajax_zetaflixgeneratepage', array($this,'insert_pages_ajax'));
            add_action('wp_ajax_zetaflixcleanerdatabase', array($this,'cleaner'));
            add_action('wp_ajax_zetaflixdbtool', array($this,'tool'));
            add_action('admin_menu', array($this,'menu'));
            // More data
            $this->set_options();
        }
        // clean browser data
        add_action('wp_ajax_nopriv_zeta_cleanbrowser', array($this,'clean_browser'));
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
    public function menu(){
        add_submenu_page(
            'tools.php',
            __z('ZetaFlix Database'),
            __z('ZetaFlix Database'),
            'manage_options',
            'zetaflix-database',
            array(&$this,'tool_page')
        );
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function tool_page(){
        $newlink = get_option('zetaflix_update_linksmodule');
        $lkey    = get_option('zetaflix_license_key');
        $lstatus = get_option('zetaflix_license_key_status');
        $timerun = get_option('_zetaflix_database_tool_runs');
        $nonce   = wp_create_nonce('zetadatabasetoolnonce');
        $never   = __z('this process was never executed');
        require_once(ZETA_DIR.'/inc/parts/admin/database_tool.php');
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
  
	public function close_widgets_notice(){
      delete_option('zetaflix_pages');
    }
	
    public function insert_widgets_ajax(){
        // Generator pages
        $this->insert_widgets();
		// Redirect page
        wp_redirect($_SERVER['HTTP_REFERER'], 301 ); exit;
    }
	
    public function insert_widgets(){
	global $wpdb;
    $mreleases  = $array = array( 3 => array( 'title' => 'Years', 'zt_scroll' => '', ), 2 => array( 'title' => 'Years', 'zt_scroll' => '', ), '_multiwidget' => 1 );
    $mgenres    = $array = array( 3 => array( 'title' => 'Genres', 'zt_count' => '', ), 2 => array( 'title' => 'Genres', 'zt_count' => '', ), '_multiwidget' => 1 );
	$popular    = $array = array( 2 => array('title' => 'Popular',  'zt_tipo' => 'all', 'zt_default' => 'movies', 'zt_count' => '5', 'zt_filter' => 'filter'), '_multiwidget' => 1 );
    $random     = $array = array( 9 => array( 'title' => '', 'zt_nun' => 7,  'zt_order' => 'desc', 'zt_layout' => 'b', 'zt_rand' => 'on'), 8 => array( 'title' => '', 'zt_nun' => 7,  'zt_order' => 'desc', 'zt_layout' => 'b', 'zt_rand' => 'on'), 7 => array( 'title' => '', 'zt_nun' => 2,  'zt_order' => 'desc', 'zt_layout' => 'a', 'zt_rand' => 'on'), 6 => array( 'title' => '', 'zt_nun' => 2,  'zt_order' => 'desc', 'zt_layout' => 'a', 'zt_rand' => 'on'), 5 => array( 'title' => '', 'zt_nun' => 2,  'zt_order' => 'desc', 'zt_layout' => 'a', 'zt_rand' => 'on'), 4 => array( 'title' => '', 'zt_nun' => 2,  'zt_order' => 'desc', 'zt_layout' => 'a', 'zt_rand' => 'on'), 3 => array( 'title' => '', 'zt_nun' => 7,  'zt_order' => 'desc', 'zt_layout' => 'b', 'zt_rand' => 'on'), 2 => array( 'title' => '', 'zt_nun' => 2,  'zt_order' => 'desc', 'zt_layout' => 'a', 'zt_rand' => 'on'), '_multiwidget' => 1 );
    $related    = $array = array( 3 => array( 'title' => '', 'zt_nun' => 7,  'zt_order' => 'desc', 'zt_layout' => 'b', 'zt_rand' => 'on'), 2 => array( 'title' => '', 'zt_nun' => 7,  'zt_order' => 'desc', 'zt_layout' => 'b', 'zt_rand' => 'on'),  '_multiwidget' => 1 );
    $blogcat	=  $array = array( 2 => array( 'title' => 'Categories' ), '_multiwidget' => 1 );
    $blogrel	=  $array = array( 2 => array( 'title' => 'Related' ), '_multiwidget' => 1 );
    $blogrec	=  $array = array( 2 => array( 'title' => 'Recent' ), '_multiwidget' => 1 );
    $blogtag	=  $array = array( 2 => array( 'title' => 'Tags' ), '_multiwidget' => 1 );
      
	update_option('widget_ztw_mreleases', $mreleases);
    update_option('widget_ztw_mgenres', $mgenres);
    update_option('widget_ztw_content_trending', $popular);
    update_option('widget_ztw_content_random', $random);
    update_option('widget_ztw_content_related', $related);
    update_option('widget_ztw_blogcat', $blogcat);
    update_option('widget_ztw_blogrelated', $blogrel);
    update_option('widget_ztw_blogrecent', $blogrec);
    update_option('widget_ztw_blogtags', $blogtag);
      
    $savewidgets = array(
    'wp_inactive_widgets' => array(),
    'widgets-home' => array(),
    'sidebar-home' => array('ztw_mgenres-2', 'ztw_content_trending-2', 'ztw_mreleases-2'),
    'sidebar-archive' => array('ztw_content_random-2', 'ztw_mgenres-3', 'ztw_content_random-3', 'ztw_mreleases-3'),
    'sidebar-movies' => array('ztw_content_random-4', 'ztw_content_related-2'),
    'sidebar-tvshows' => array('ztw_content_random-5', 'ztw_content_related-3'),
    'sidebar-seasons' => array('ztw_content_random-6', 'ztw_content_random-8'),
    'sidebar-episodes' => array('ztw_content_random-7', 'ztw_content_random-9'),
    'sidebar-blog-single' => array('ztw_blogrelated-2', 'ztw_blogrecent-2'),
    'sidebar-blog-archive' => array('ztw_blogcat-2', 'ztw_blogtags-2'),
    'array_version' => 3
	);
    update_option('sidebars_widgets', $savewidgets);
      
    //confirm changes
    update_option('zetaflix_widgets', true );       
    }
	
	
    public function insert_pages_ajax(){
        // Generator pages
        $this->insert_pages();
		// Redirect page
        wp_redirect($_SERVER['HTTP_REFERER'], 301 ); exit;
    }

    public function insert_pages(){
        // Compose pages
		$pages = array(
			'pagetrending' => array(
				'title' => __z('Trending'),
                'name' => false,
				'tpl' => 'trending'
			),
			'pageratings' => array(
				'title' => __z('Ratings'),
                'name' => false,
				'tpl' => 'rating'
			),
			'pageaccount' => array(
				'title' => __z('Account'),
                'name' => false,
				'tpl' => 'account'
			),
			'pagecontact' => array(
				'title' => __z('Contact'),
                'name' => false,
				'tpl' => 'contact'
			),
			'pageblog' => array(
				'title' => __z('Blog'),
                'name' => false,
				'tpl' => 'blog'
			),
			'pagetopimdb' => array(
				'title' => __z('TOP IMDb'),
				'name' => 'imdb',
				'tpl' => 'topimdb'
			),
			'jwpage' => array(
				'title' => __z('JW Player'),
				'name' => 'jwplayer',
				'tpl' => 'jwplayer'
			)
		);

		// Insert Pages
		foreach($pages as $key => $value){
            $post_id = wp_insert_post(array(
                'post_name' 	 => $value['name'] ? $value['name'] : $value['title'],
                'post_title' 	 => $value['title'],
                'post_status' 	 => 'publish',
                'post_type' 	 => 'page',
                'ping_status' 	 => 'closed',
                'comment_status' => 'closed',
                'page_template'  => 'pages/'.$value['tpl'].'.php'
            ));
            zetaflix_set_option($key, $post_id);
		}
		// Update Option Pages
		update_option('zetaflix_pages', true );
    }


    public function tool(){
        $run = zeta_isset($_POST,'run');
        $noc = zeta_isset($_POST,'noc');

        if($run && wp_verify_nonce($noc, 'zetadatabasetoolnonce')){

            global $wpdb;

            $time = get_option('_zetaflix_database_tool_runs');
            $time[$run] = time();

            switch ($run) {
                
              case 'genwidgets':
                    $this->insert_widgets();
                    $remove = false;
                	break;

                case 'genpages':
                    $this->insert_pages();
                    $remove = false;
                    break;

                case 'transients':
                    $wpdb->query("DELETE FROM {$wpdb->options} WHERE `option_name` LIKE (\"%\_transient\_%\")");
                    $remove = false;
                    break;

                case 'license':
                    delete_option('zetaflix_license_key');
                    delete_option('zetaflix_license_key_status');
                    delete_option('_transient_zetaflix-update-response');
                    delete_option('_transient_zetaflix_license_message');
                    delete_option('_transient_timeout_zetaflix_license_message');
                    $remove = false;
                    break;

                case 'userfavorites':
                    $this->delete('usermeta','meta_key',$wpdb->base_prefix.'user_list_count');
                    $this->delete('postmeta','meta_key','_zt_list_users');
                    $remove = false;
                    break;

                case 'userviews':
                    $this->delete('usermeta','meta_key',$wpdb->base_prefix.'user_view_count');
                    $this->delete('postmeta','meta_key','_zt_views_users');
                    $remove = false;
                    break;

                case 'featured':
                    $this->delete('postmeta','meta_key','zt_featured_post');
                    $remove = false;
                    break;

                case 'reports':
                    $this->delete('postmeta','meta_key','numreport');
                    $remove = false;
                    break;

                case 'postviews':
                    $this->delete('postmeta','meta_key','zt_views_count');
                    $remove = false;
                    break;

                case 'ratings':
                    $this->delete('postmeta','meta_key','_starstruck_total');
                    $this->delete('postmeta','meta_key','_starstruck_avg');
                    $this->delete('postmeta','meta_key','_starstruck_data');
                    $remove = false;
                    break;
              	case 'closenoti':
                    $noti = zeta_isset($_POST,'not');
                	if($noti == 'pages'){
                		update_option('zetaflix_pages', true);
                    }elseif($noti == 'widgets'){
                      update_option('zetaflix_widgets', true);
                    }
                	break;
            }
        }
        // Update data base
        update_option('_zetaflix_database_tool_runs', $time );
        // Json Response
        wp_send_json(array('response' => true, 'remove' => $remove, 'message' => __z('Just now')));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function clean_browser(){
        $response = array('response' => 'uncleaned');
        if(delete_transient('browser_afa64b13fd8e798c1557c1c693e93bd5')){
            $response = array('response' => 'cleansed');
        }
        wp_send_json($response);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function set_options(){

        /* Converse Codestar Options
        -------------------------------------------------------------------------------
        */
        $converse_options = array(
            'iperpage' => 'posts_per_page'
        );

        foreach($converse_options as $csopt => $wpopt){
            $option_1 = zeta_get_option($csopt);
            $option_2 = get_option($wpopt);
            if($option_1 !== $option_2){
                update_option($wpopt,$option_1);
            }
        }

        /* Data Base Options
        -------------------------------------------------------------------------------
        */
        if(empty(get_option('zetaflix_database'))){
            update_option('zetaflix_database', ZETA_VERSION_DB);
            update_option('zetaflix_update_linksmodule',true);
        }

        if(empty(get_option('zetaflix_pages'))){
            update_option('zetaflix_pages',false);
        }


        /* Slun Options
        -------------------------------------------------------------------------------
        */
        if(empty(get_option('zt_author_slug'))) {
        	update_option('zt_author_slug', 'user');
        }

        if(empty(get_option('zt_movies_slug'))){
        	update_option('zt_movies_slug', 'movies');
        }

        if(empty(get_option('zt_requests_slug'))) {
        	update_option('zt_requests_slug', 'requests');
        }

        if(empty(get_option('zt_tvshows_slug'))){
        	update_option('zt_tvshows_slug', 'tvshows');
        }

        if(empty(get_option('zt_seasons_slug'))){
        	update_option('zt_seasons_slug', 'seasons');
        }

        if(empty(get_option('zt_episodes_slug'))){
        	update_option('zt_episodes_slug', 'episodes');
        }

        if(empty(get_option('zt_links_slug'))){
        	update_option('zt_links_slug', 'links');
        }

        if(empty(get_option('zt_genre_slug'))){
        	update_option('zt_genre_slug', 'genre');
        }

        if(empty(get_option('zt_release_slug'))){
        	update_option('zt_release_slug', 'release');
        }

        if(empty(get_option('zt_network_slug'))){
        	update_option('zt_network_slug', 'network');
        }

        if(empty(get_option('zt_studio_slug'))){
        	update_option('zt_studio_slug', 'studio');
        }

        if(empty(get_option('zt_cast_slug'))){
        	update_option('zt_cast_slug', 'cast');
        }

        if(empty(get_option('zt_creator_slug'))){
        	update_option('zt_creator_slug', 'creator');
        }

        if(empty(get_option('zt_director_slug'))){
        	update_option('zt_director_slug', 'director');
        }

        if(empty(get_option('zt_quality_slug'))){
        	update_option('zt_quality_slug', 'quality');
        }
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function cleaner(){

        global $wpdb;

        // Database 3.0
        $this->delete('postmeta', 'meta_key', 'zt_cast');
        $this->delete('postmeta', 'meta_key', 'zt_dir');
        // Clean database
        $this->delete('postmeta', 'meta_key', 'status');
        $this->delete('postmeta', 'meta_key', '_user_liked');
        $this->delete('postmeta', 'meta_key', '_post_like_modified');
        $this->delete('postmeta', 'meta_key', '_post_like_count');
        $this->delete('usermeta', 'meta_key', $wpdb->base_prefix.'_user_like_count');

        // Get old config
        $oldnew = array(
            'zt_google_analytics'         => 'ganalytics',
            'zt_font_style'               => 'font',
            'zt_color_style'              => 'style',
            'color1'                      => 'maincolor',
            'color2'                      => 'bgcolor',
            'zt_custom_css'               => 'css',
            'zt_footer_copyright'         => 'footercopyright',
            'zt_footer_text'              => 'footertext',
            'zt_footer_tt1'               => 'footerc1',
            'zt_footer_tt2'               => 'footerc2',
            'zt_footer_tt3'               => 'footerc3',
            'zt_vplayer_timeout'          => 'playwait',
            'zt_jw_librarie'              => 'jwlibrary',
            'zt_jw_key'                   => 'jwkey',
            'zt_jw_abouttext'             => 'jwabout',
            'zt_jw_skinactive'            => 'jwcolor',
            'zt_welcome_mail_user'        => 'welcomemsg',
            'zt_app_id_facebook'          => 'fbappid',
            'zt_admin_facebook'           => 'fbadmin',
            'zt_app_language_facebook'    => 'fblang',
            'zt_scheme_color_facebook'    => 'fbscheme',
            'zt_number_comments_facebook' => 'fbnumber',
            'zt_ft_title'                 => 'featuredtitle',
            'zt_ft_number_items'          => 'featureditems',
            'zt_blo_title'                => 'blogtitle',
            'zt_blo_number_items'         => 'blogitems',
            'zt_slider_items'             => 'slideritems',
            'zt_slider_speed'             => 'sliderspeed',
            'zt_mm_title'                 => 'movietitle',
            'zt_mm_number_items'          => 'movieitems',
            'zt_topimdb_title'            => 'topimdbtitle',
            'zt_topimdb_number_items'     => 'topimdbitems',
            'zt_mt_title'                 => 'tvtitle',
            'zt_mt_number_items'          => 'tvitems',
            'zt_ms_title'                 => 'seasonstitle',
            'zt_ms_number_items'          => 'seasonsitems',
            'zt_me_title'                 => 'episodestitle',
            'zt_me_number_items'          => 'episodesitems',
            'zt_languages_post_link'      => 'linkslanguages',
            'zt_quality_post_link'        => 'linksquality',
            'zt_ountdown_link_redirect'   => 'linktimewait',
            'zt_alt_name'                 => 'seoname',
            'zt_main_keywords'            => 'seokeywords',
            'zt_metadescription'          => 'seodescription'
        );

        foreach ($oldnew as $old => $new) {
            $option = get_option($old);
            if($option){
                zetaflix_set_option($new, $option);
            }
        }


        // Delete options
        $options = array(
            'zt_main_slider_items','zt_clear_database_time','zt_main_slider_speed','zt_main_slider_order','zt_main_slider_autoplay','zt_main_slider_radom',
            'zt_main_slider','zt_shorcode_home','zt_api_release_date','zt_api_upload_poster','zt_api_genres','zt_api_language',
            'zt_api_key','zt_activate_api','_site_register_in_omgdb','zt_register_note','wp_app_dbmkey','minify_html_comments',
            'minify_html_active','zt_cleardb_date','zt_jw_skinbackground','zt_jw_skininactive','zt_jw_skinname','zt_jwplayer_page_gdrive',
            'zt_grprivate_key','zt_grpublic_key','zt_player_ads_300','zt_player_ads_time','zt_player_ads_hide_clic','zt_player_ads',
            'zt_player_views','zt_player_quality','zt_player_report','zt_player_luces','zt_google_analytics','posts_per_page_blog',
            'zt_posts_page','zt_account_page','zt_trending_page','zt_rating_page','zt_contact_page','zt_topimdb_page',
            'zt_top_imdb_items','zt_header_code','zt_footer_code','zt_play_trailer','zt_similiar_titles','zt_live_search',
            'zt_font_style','zt_color_style','color1','color2','zt_custom_css','zt_logo','ads_ss_1','ads_ss_2','ads_ss_3',
            'zt_favicon','zt_touch_icon','zt_logo_admin','zt_defaul_footer','zt_footer_copyright','zt_logo_footer',
            'zt_footer_text','zt_footer_tt1','zt_footer_tt2','zt_footer_tt3','zt_vplayer_autoload','zt_vplayer_width',
            'zt_vplayer_timeout','zt_vplayer_ads','zt_jw_librarie','zt_jwplayer_page','zt_jw_abouttext','zt_jw_skinactive',
            'zt_jw_logo','zt_jw_logo_position','zt_commets','zt_app_id_facebook','zt_admin_facebook','zt_app_language_facebook',
            'zt_scheme_color_facebook','zt_number_comments_facebook','zt_shortname_disqus','zt_home_sortable','zt_ft_title','zt_ft_number_items',
            'zt_featured_slider_ac','zt_blo_title','zt_blo_number_items','zt_blo_number_words','zt_slider_items','zt_slider_speed',
            'zt_slider_radom','zt_mm_title','zt_mm_number_items','zt_mm_activate_slider','zt_mt_title','zt_mt_number_items',
            'zt_mt_activate_slider','zt_ms_title','zt_ms_number_items','zt_me_title','zt_me_number_items','zt_topimdb_layout',
            'zt_topimdb_title','zt_topimdb_number_items','zt_activate_post_links','zt_languages_post_link','zt_quality_post_link','zt_ountdown_link_redirect',
            'zt_links_table_size','zt_links_table_added','zt_links_table_quality','zt_links_table_language','zt_links_table_user','zt_welcome_mail_user',
            'zt_site_titles','zt_alt_name','zt_main_keywords','zt_metadescription','zt_veri_google','zt_veri_alexa',
            'zt_veri_bing','zt_veri_yandex','ads_spot_home','ads_spot_300','ads_spot_468','ads_spot_single','ads_ss_4','zt_redirect_post_links',
            'zt_menu_framework_secion','zt_vplayer_autoplay_youtube','zt_vplayer_autoplay_jwplayer','zt_vplayer_autoplay','zt_remove_ver','zt_minify_html',
            'zt_minify_html_comments','zt_jw_key','zt_dynamic_bg','zt_register_user','zt_emoji_disable','zt_layout_full_width',
            'zt_autoplay_s','zt_autoplay_s_movies','zt_autoplay_s_tvshows','zt_mm_autoplay_slider','zt_mm_random_order','zt_featured_slider_ap',
            'zt_mt_autoplay_slider','zt_mt_random_order','zt_ms_autoplay_slider','zt_ms_random_order','zt_me_autoplay_slider','zt_me_random_order'
        );

        foreach ($options as $key) {
            delete_option($key);
        }

        // Update Option Pages
		update_option('zetaflix_database', ZETA_VERSION_DB );

        // Redirect page
		wp_redirect($_SERVER['HTTP_REFERER'], 301 ); exit;
    }


    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function delete($table, $row, $key){
        if($table && $row && $key){
            global $wpdb;
            $wpdb->delete($wpdb->prefix.$table, array($row => $key));
        }
    }

}

new ZetaDatabase;
