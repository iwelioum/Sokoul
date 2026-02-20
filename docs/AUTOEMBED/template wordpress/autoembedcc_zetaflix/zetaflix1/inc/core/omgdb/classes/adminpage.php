<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

class OmegadbAdminPage extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        add_action('admin_menu',array(&$this,'DbmvMenu'));
        add_action('after_setup_theme',array(&$this,'Setup'));
    }

    /**
     * @since 1.0.0
     * @version 3.0
     */
    public function Setup(){

        // Omegadb Options
        $settings = get_option(OMEGADB_OPTIONS);
        $imdbdata = get_option(OMEGADB_OPTIMDB);

        // Set Settings default
        if(!$settings){
            $data = array(
                'omegadb'          => '',
                'themoviedb'        => OMEGADB_TMDBKEY,
                'language'          => 'en-US',
                'upload'            => 1,
                'genres'            => 1,
                'release'           => 1,
                'autoscroll'        => 1,
                'nospostimp'        => 0,
                'repeated'          => 0,
                'safemode'          => 0,
                'autoscrollresults' => '200',
                'delaytime'         => '1000',
                'titlemovies'       => '{name}',
                'titletvshows'      => '{name}',
                'titleseasons'      => __z('{name}: Season {season}'),
                'titlepisodes'      => '{name}: {season}x{episode}',
                'content-movies'    => '<!-- wp:paragraph --><p>{synopsis}</p><!-- /wp:paragraph -->',
                'content-tvshows'   => '<!-- wp:paragraph --><p>{synopsis}</p><!-- /wp:paragraph -->',
                'updatermethod'     => 'wp-ajax',
                'updatermovies'     => 1,
                'updatershows'      => 1,
                'updaterseasons'    => 1,
                'updaterepisodes'   => 1,
                'request-email'     => '',
                'requestsunk'       => 0,
                'reqauto-adm'       => 1,
                'reqauto-edi'       => 1,
                'reqauto-aut'       => 1,
                'reqauto-con'       => 1,
                'reqauto-sub'       => 1,
                'reqauto-unk'       => 0,
                'phptimelimit'      => '300',
                'phpmemorylimit'    => '256',
                'orderseasons'      => 'ASC',
                'orderepisodes'     => 'ASC',
                'pstatusmovies'     => 'publish',
                'pstatustvshows'    => 'publish',
                'pstatusseasons'    => 'publish',
                'pstatusepisodes'   => 'publish',
                'gutenberg-movies'  => 1,
                'gutenberg-tvshows' => 1,
                'gutenberg-seasons' => 1,
                'gutenberg-episode' => 1,
                'gutenberg-links'   => 1,
            );
            // Update Option
            update_option(OMEGADB_OPTIONS, $data);
        }

        // Set IMDb Data
        if(!$imdbdata){
            $data = array(
                'time' => time(),
                'page' => '1'
            );
            // Update Option
            update_option(OMEGADB_OPTIMDB, $data);
        }
    }

    /**
     * @since 1.0.0
     * @version 3.0
     */
    public function DbmvMenu(){
        add_menu_page( __z('Omegadb'), __z('Omegadb'), 'manage_options', 'omgdb', array(&$this,'DbmvApp'), 'dashicons-superhero', 2);
        add_submenu_page('omgdb', __z('Omegadb - Settings'), __z('Settings').$this->PendingNotice(), 'manage_options', 'omgdb-settings',array(&$this,'DbmvSettings'));
    }

    /**
     * @since 1.0.0
     * @version 3.0
     */
    public function DbmvApp(){
        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/admin_app.php');
    }

    /**
     * @since 1.0.0
     * @version 3.0
     */
    public function DbmvSettings(){
        require_once get_parent_theme_file_path('/inc/core/omgdb/tpl/admin_settings.php');
    }

    /**
     * @since 1.0.0
     * @version 3.0
     */
    public function PendingNotice(){
        if(empty($this->get_option('omegadb')) || empty($this->get_option('themoviedb'))){
            return "<span class='awaiting-mod' style='margin-left:10px'><span class='pending-count'>1</span></span>";
        }
    }
}

new OmegadbAdminPage;
