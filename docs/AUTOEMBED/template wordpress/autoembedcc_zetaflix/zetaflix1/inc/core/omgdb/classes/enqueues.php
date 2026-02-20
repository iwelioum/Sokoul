<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/


class OmegadbEnqueues extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        add_action('admin_enqueue_scripts', array(&$this,'Enqueues'));
    }

    /**
     * @since 1.0.0
     * @version 3.1
     */
    public function Enqueues(){
        $parameters = array(
            'formcot' => zeta_get_option('contact_form') ? true : false,
            'formrpt' => zeta_get_option('report_form') ? true : false,
            'dapikey' => $this->get_option('omegadb'),
            'tapikey' => $this->get_option('themoviedb',OMEGADB_TMDBKEY),
            'apilang' => $this->get_option('language','en-US'),
            'extimer' => $this->get_option('delaytime','500'),
            'rscroll' => $this->get_option('autoscrollresults','200'),
            'inscrll' => $this->get_option('autoscroll'),
            'safemod' => $this->get_option('safemode'),
            'titmovi' => $this->get_option('titlemovies'),
            'tittvsh' => $this->get_option('titletvshows'),
            'titseas' => $this->get_option('titleseasons'),
            'titepis' => $this->get_option('titlepisodes'),
            'noposti' => $this->get_option('nospostimp'),
            'pupload' => $this->get_option('upload'),
            'upmethd' => $this->get_option('updatermethod'),
            'csectin' => $this->Disset($_GET,'section'),
            'gerepis' => $this->Disset($_GET,'generate_episodes'),
            'upstats' => get_option('__omgdb_cronmeta_status','paused'),
            'uppaged' => get_option('__omgdb_cronmeta_paged','1'),
            'uptotal' => get_option('__omgdb_cronmeta_total','0'),
            'uppages' => get_option('__omgdb_cronmeta_pages'),
			'posturl' => admin_url('post.php?post='),
            'ajaxurl' => admin_url('admin-ajax.php','relative'),
			'tmdburl' => esc_url('https://www.themoviedb.org/'),
            'dapiurl' => esc_url(OMEGADB_DBMVAPI),
            'tapiurl' => esc_url(OMEGADB_TMDBAPI),
            'prsseng' => __z('Processing..'),
            'nointrn' => __z('There is no Internet connection'),
            'dbmverr' => __z('Our services are out of line, please try again later'),
            'tmdberr' => __z('The title does not exist or resources are not available at this time'),
            'misskey' => __z('You have not added an API key for Omegadb'),
            'loading' => __z('Loading..'),
            'loadmor' => __z('Load More'),
            'import'  => __z('Import'),
            'save'    => __z('Save'),
            'savech'  => __z('Save Changes'),
            'saving'  => __z('Saving..'),
            'uerror'  => __z('Unknown error'),
            'nerror'  => __z('Connection error'),
            'aerror'  => __z('Api key invalid or blocked'),
            'nocrdt'  => __z('There are not enough credits to continue'),
            'complt'  => __z('Process Completed'),
            'welcom'  => __z('Welcome, the service has started successfully'),
			'started' => __z('Started'),
			'stopped'  => __z('Stopped'),
            'cllogs'  => __z('Log cleaned'),
            'imprted' => __z('Imported'),
            'updated' => __z('Updated'),
            'editxt'  => __z('Edit'),
            'nocont'  => __z('No content available'),
            'timest'  => array(
                'second'  => __z('Second'),
                'seconds' => __z('Seconds'),
                'minute'  => __z('Minute'),
                'minutes' => __z('Minutes'),
                'hour'    => __z('Hour'),
                'hours'   => __z('Hours'),
                'day'     => __z('Day'),
                'days'    => __z('Days'),
                'week'    => __z('Week'),
                'weeks'   => __z('Weeks'),
                'month'   => __z('Month'),
                'months'  => __z('Months'),
                'year'    => __z('Year'),
                'years'   => __z('Years')
            )
        );
        // All Scripts
        wp_enqueue_style('omegadb-app', OMEGADB_URI.'/assets/omegadb'.$this->Minify().'.css', array(), OMEGADB_VERSION);
        wp_enqueue_script('omegadb-app', OMEGADB_URI.'/assets/omegadb'.$this->Minify().'.js', array('jquery'), OMEGADB_VERSION);
        wp_localize_script('omegadb-app', 'omegadb', $parameters);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    private function Minify(){
        return (WP_DEBUG && defined('WP_ZETATHEMES_DEV')) ? '' : '.min';
    }

}

new OmegadbEnqueues;
