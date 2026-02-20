<?php
/*
* ----------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* ----------------------------------------------------
* @since 1.0.0
*/

class OmegadbPosTypes extends OmegadbHelpers{

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function __construct(){
        add_action('init', array(&$this,'movies'), 0);
        add_action('init', array(&$this,'tvshows'), 0);
        add_action('init', array(&$this,'seasons'), 0);
        add_action('init', array(&$this,'episodes'), 0);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function movies(){
		$arguments = array(
            'labels' => array(
    			'name'           => __z('Movies'),
    			'singular_name'  => __z('Movie'),
    			'menu_name'      => __z('Movies'),
    			'name_admin_bar' => __z('Movies'),
    			'all_items'      => __z('Movies')
    		),
            'rewrite' => array(
    			'slug'       => get_option('zt_movies_slug','movies'),
    			'with_front' => true,
    			'pages'      => true,
    			'feeds'      => true
    		),
			'label'               => __z('Movies'),
			'description'         => __z('Movies manage'),
			'supports'            => array('title', 'editor','comments','thumbnail','author'),
			'taxonomies'          => array('genres','ztquality'),
			'hierarchical'        => false,
			'public'              => true,
			'show_ui'             => true,
			'show_in_menu'        => true,
            'show_in_rest'        => (zeta_get_option('classic_editor') == true) ? false : true,
			'menu_position'       => 5,
			'menu_icon'           => 'dashicons-editor-video',
			'show_in_admin_bar'   => true,
			'show_in_nav_menus'   => false,
			'can_export'          => true,
			'has_archive'         => true,
			'exclude_from_search' => false,
			'publicly_queryable'  => true,
			'capability_type'     => 'post'
		);
		register_post_type('movies',$arguments);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function tvshows(){
		$arguments = array(
            'labels' => array(
                'name'           => __z('TV Shows'),
    			'singular_name'  => __z('TV Shows'),
    			'menu_name'      => __z('TV Shows'),
    			'name_admin_bar' => __z('TV Shows'),
    			'all_items'      => __z('TV Shows')
    		),
            'rewrite' => array(
    			'slug'       => get_option('zt_tvshows_slug','tvshows'),
    			'with_front' => true,
    			'pages'      => true,
    			'feeds'      => true
    		),
			'label'               => __z('TV Show'),
			'description'         => __z('TV series manage'),
			'supports'            => array('title', 'editor','comments','thumbnail','author'),
			'taxonomies'          => array('genres'),
			'hierarchical'        => false,
			'public'              => true,
			'show_ui'             => true,
            'show_in_rest'        => (zeta_get_option('classic_editor') == true) ? false : true,
			'show_in_menu'        => true,
			'menu_position'       => 5,
			'menu_icon'           => 'dashicons-welcome-view-site',
			'show_in_admin_bar'   => true,
			'show_in_nav_menus'   => false,
			'can_export'          => true,
			'has_archive'         => true,
			'exclude_from_search' => false,
			'publicly_queryable'  => true,
			'capability_type'     => 'post'
		);
		register_post_type('tvshows',$arguments);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function seasons(){
		$arguments = array(
            'labels' => array(
                'name'           => __z('Seasons'),
    			'singular_name'  => __z('Seasons'),
    			'menu_name'      => __z('Seasons'),
    			'name_admin_bar' => __z('Seasons'),
    			'all_items'      => __z('Seasons')
    		),
            'rewrite' => array(
    			'slug'       => get_option('zt_seasons_slug','seasons'),
    			'with_front' => true,
    			'pages'      => true,
    			'feeds'      => true,
    		),
			'label'               => __z('Seasons'),
			'description'         => __z('Seasons manage'),
			'supports'            => array('title', 'editor','comments','thumbnail','author'),
			'taxonomies'          => array( ),
			'hierarchical'        => false,
			'public'              => true,
			'show_ui'             => true,
            'show_in_rest'        => (zeta_get_option('classic_editor') == true) ? false : true,
			'show_in_menu'        => true,
			'menu_position'       => 5,
			'show_in_menu'        => 'edit.php?post_type=tvshows',
			'menu_position'       => 20,
			'show_in_nav_menus'   => false,
			'can_export'          => true,
			'has_archive'         => true,
			'exclude_from_search' => true,
			'publicly_queryable'  => true,
			'capability_type'     => 'post',
		);
		register_post_type('seasons',$arguments);
    }

    /**
     * @since 1.0.0
     * @version 1.0
     */
    public function episodes(){
    	$arguments = array(
            'labels' => array(
                'name'           => __z('Episodes'),
    			'singular_name'  => __z('Episodes'),
    			'menu_name'      => __z('Episodes'),
    			'name_admin_bar' => __z('Episodes'),
    			'all_items'      => __z('Episodes')
        	),
            'rewrite' => array(
        		'slug'       => get_option('zt_episodes_slug','episodes'),
        		'with_front' => true,
        		'pages'      => true,
        		'feeds'      => true,
        	),
    		'label'               => __z('Episodes'),
    		'description'         => __z('Episodes manage'),
    		'supports'            => array('title', 'editor','comments','thumbnail','author'),
    		'taxonomies'          => array('ztquality'),
    		'hierarchical'        => false,
    		'public'              => true,
    		'show_ui'             => true,
            'show_in_rest'        => (zeta_get_option('classic_editor') == true) ? false : true,
    		'show_in_menu'        => true,
    		'menu_position'       => 5,
    		'show_in_menu'        => 'edit.php?post_type=tvshows',
    		'menu_position'       => 20,
    		'show_in_nav_menus'   => false,
    		'can_export'          => true,
    		'has_archive'         => true,
    		'exclude_from_search' => true,
    		'publicly_queryable'  => true,
    		'capability_type'     => 'post',
    	);
    	register_post_type('episodes',$arguments);
    }
}

new OmegadbPosTypes;
