<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/


class OmegadbTaxonomies extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        // All Taxonomies
		add_action('init', array(&$this,'Keywords'), 0);
        add_action('init', array(&$this,'Genres'), 0);
        add_action('init', array(&$this,'Quality'), 0);
        add_action('init', array(&$this,'Director'), 0);
        add_action('init', array(&$this,'Creator'), 0);
        add_action('init', array(&$this,'Cast'), 0);
        add_action('init', array(&$this,'Studio'), 0);
        add_action('init', array(&$this,'Network'), 0);
        add_action('init', array(&$this,'Year'), 0);
		add_action('init', array(&$this,'Country'), 0);
		add_action('init', array(&$this,'Language'), 0);
        // Support Tags
        add_action('pre_get_posts', array(&$this,'cpttags'));
        // Fixing
        add_action('after_switch_theme', array(&$this,'SwitchTheme'));
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Genres(){
        $taxonomy = array(
            'label'	  => __z('Genres'),
            'rewrite' => array(
                'slug' => get_option('zt_genre_slug','genre')
            ),
            'show_admin_column' => false,
            'hierarchical'		=> true,
            'show_in_rest'      => true
        );
        register_taxonomy('genres', array('tvshows','movies'), $taxonomy);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Quality(){
        $taxonomy = array(
            'label'	  => __z('Quality'),
            'rewrite' => array(
                'slug' => get_option('zt_quality_slug','quality')
            ),
            'show_admin_column' => false,
            'hierarchical'		=> true,
            'show_in_rest'      => true
        );
        register_taxonomy('ztquality', array('seasons','tvshows','episodes','movies'), $taxonomy);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Director(){
        $labels = array(
    		'name'          => __z('Director'),
    		'singular_name' => __z('Director'),
    		'menu_name'     => __z('Director'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_director_slug','director'),
    		'with_front'   => true,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
            'show_in_rest'      => true,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztdirector', array('episodes', 'movies'), $taxonomy);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Creator(){
        $labels = array(
    		'name'          => __z('Creator'),
    		'singular_name' => __z('Creator'),
    		'menu_name'     => __z('Creator'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_creator_slug','creator'),
    		'with_front'   => true,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
            'show_in_rest'      => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztcreator', array('seasons', 'tvshows'), $taxonomy);
    }
	
    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Cast(){
        $labels = array(
    		'name'          => __z('Cast'),
    		'singular_name' => __z('Cast'),
    		'menu_name'     => __z('Cast'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_cast_slug','cast'),
    		'with_front'   => true,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
            'show_in_rest'      => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztcast', array('episodes', 'seasons', 'tvshows','movies'), $taxonomy);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Studio(){
        $labels = array(
    		'name'          => __z('Studio'),
    		'singular_name' => __z('Studio'),
    		'menu_name'     => __z('Studio'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_studio_slug','studio'),
    		'with_front'   => true,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
            'show_in_rest'      => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztstudio', array('seasons', 'tvshows'), $taxonomy);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Network(){
        $labels = array(
    		'name'          => __z('Networks'),
    		'singular_name' => __z('Networks'),
    		'menu_name'     => __z('Networks'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_network_slug','network'),
    		'with_front'   => true,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
            'show_in_rest'      => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztnetworks', array('seasons', 'tvshows'), $taxonomy);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Year(){
        $labels = array(
    		'name'          => __z('Year'),
    		'singular_name' => __z('Year'),
    		'menu_name'     => __z('Year'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_release_slug','release'),
    		'with_front'   => true,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
            'show_in_rest'      => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztyear', array('episodes', 'seasons', 'tvshows','movies'), $taxonomy);
    }
	
    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Country(){
        $labels = array(
    		'name'          => __z('Country'),
    		'singular_name' => __z('Country'),
    		'menu_name'     => __z('Country'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_country_slug','country'),
    		'with_front'   => true,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
            'show_in_rest'      => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztcountry', array('seasons', 'tvshows','movies'), $taxonomy);
    }
	
    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Language(){
        $taxonomy = array(
            'label'	  => __z('Language'),
            'rewrite' => array(
                'slug' => get_option('zt_language_slug','language')
            ),
            'show_admin_column' => false,
            'hierarchical'		=> true,
            'show_in_rest'      => true
        );
        register_taxonomy('ztlanguage', array('tvshows','movies', 'seasons', 'episodes'), $taxonomy);
    }
	
    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function Keywords(){
        $labels = array(
    		'name'          => __z('Keywords'),
    		'singular_name' => __z('Keyword'),
    		'menu_name'     => __z('Keywords'),
    	);
    	$rewrite = array(
    		'slug'         => get_option('zt_keywords_slug','keywords'),
    		'with_front'   => false,
    		'hierarchical' => false,
    	);
    	$taxonomy = array(
    		'labels'            => $labels,
            'rewrite'           => $rewrite,
    		'hierarchical'      => false,
    		'public'            => true,
    		'show_ui'           => true,
            'show_in_rest'      => true,
    		'show_admin_column' => false,
    		'show_in_nav_menus' => false,
    		'show_tagcloud'     => true
    	);
    	register_taxonomy('ztkeywords', array('tvshows','movies','seasons','episodes'), $taxonomy);
    }
	


    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function cpttags($query){
        if($query->is_tag() && $query->is_main_query()) {
            $query->set('post_type', array('movies','tvshows'));
        }
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function SwitchTheme(){
        flush_rewrite_rules();
    }
}

new OmegadbTaxonomies;
