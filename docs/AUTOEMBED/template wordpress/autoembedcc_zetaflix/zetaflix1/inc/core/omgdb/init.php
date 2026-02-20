<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

if(!class_exists('Omegadb')){
    class Omegadb{

        /**
         * @since 1.0.0
         * @version 3.0
         */
        public function __construct(){
            $this->Init();
        }

        /**
         * @since 1.0.0
         * @version 3.0
         */
        public function Init(){
            // Defined Constants
            define('OMEGADB_VERSION','1.0.0');
            define('OMEGADB_OPTIONS','_omegadb_settings');
            define('OMEGADB_OPTIMDB','_omegadb_imdbdata');
			define('OMEGADB_HOME','https://cdn.bescraper.cf/api');
            define('OMEGADB_DBMVCDN','https://cdn.bescraper.cf/api');
			define('OMEGADB_DBMVAPI','https://cdn.bescraper.cf/api');
            define('OMEGADB_TMDBAPI','https://api.themoviedb.org/3');
			define('OMEGADB_TMDBURL', 'https://themoviedb.org');
            define('OMEGADB_TMDBKEY','eeee41dd5c435331be5827f514fc263a');
            // Locale Path
            define('OMEGADB_URI', get_template_directory_uri().'/inc/core/omgdb');
            define('OMEGADB_BAS', '');
            // Cache Persistent
            define('OMEGADB_CACHE_TIM', 172800);
            define('OMEGADB_CACHE_DIR', get_template_directory().'/inc/core/omgdb/cache/');
			define('OMEGADB_CACHE_UPD_DIR', get_template_directory().'/inc/core/omgdb/cache/updater/');
            // Actions
            add_action('load-edit.php', array(&$this,'AdminFilters'));
            add_action('post.php', array(&$this,'SeasonEpisodeGen'));
            // Application files
            require get_parent_theme_file_path('/inc/core/omgdb/classes/helpers.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/enqueues.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/importers.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/updater.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/filters.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/client.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/ajax.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/adminpage.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/taxonomies.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/epsemboxes.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/metaboxes.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/saveposts.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/postypes.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/tables.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/requests.php');
            require get_parent_theme_file_path('/inc/core/omgdb/classes/inboxes.php');
            if(zeta_get_option('report_form') == true || zeta_get_option('contact_form') == true){
                require get_parent_theme_file_path('/inc/core/omgdb/classes/dashboard.php');
            }
            // Application Functions
            require get_parent_theme_file_path('/inc/core/omgdb/functions.php');
        }

        /**
         * @since 1.0.0
         * @version 3.0
         */
        private function Locale_path(){
            $dirname        = wp_normalize_path( dirname( __FILE__ ) );
            $plugin_dir     = wp_normalize_path( WP_PLUGIN_DIR );
            $located_plugin = ( preg_match( '#'. preg_replace( '/[^A-Za-z]/', '', $plugin_dir ) .'#', preg_replace( '/[^A-Za-z]/', '', $dirname ) ) ) ? true : false;
            $directory      = ( $located_plugin ) ? $plugin_dir : get_template_directory();
            $directory_uri  = ( $located_plugin ) ? WP_PLUGIN_URL : get_template_directory_uri();
            $basename       = str_replace( wp_normalize_path( $directory ), '', $dirname );
            $dir            = $directory.$basename;
            $uri            = $directory_uri.$basename;
            $all_path = array(
                'bas' => wp_normalize_path($basename),
                'dir' => wp_normalize_path($dir),
                'uri' => $uri
            );
            return apply_filters('omgdb_get_path_locate',$all_path);
        }

        /**
         * @since 1.0.0
         * @version 3.0
         */
        public function AdminFilters(){
            $screen = get_current_screen();
            switch($screen->id) {
                case 'edit-movies':
                    add_action('in_admin_footer', function(){
                        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/import_movies.php');
                    });
                    break;
                case 'edit-tvshows':
                    add_action('in_admin_footer', function(){
                        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/import_tvshows.php');
                        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/import_seaepis.php');						
                    });
                    break;
                case 'edit-seasons':
                    add_action('in_admin_footer', function(){
                        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/import_seaepis.php');
                    });
                    add_action('manage_posts_extra_tablenav', function(){
						require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/update_seaepis.php');
						
					});
					break;
                case 'edit-episodes':				
                    add_action('manage_posts_extra_tablenav', function(){
						require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/update_seaepis.php');
					});

            }
        }
    }
    new Omegadb;
}
