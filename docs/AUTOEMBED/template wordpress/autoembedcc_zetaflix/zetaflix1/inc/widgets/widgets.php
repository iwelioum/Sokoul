<?php
/* 
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @copyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/
// Genres Mod
function widgets_home()
{
	register_sidebar(array(
		'name' => __z('Genre Modules [Homepage]') ,
		'id' => 'widgets-home',
		'description' => __z('Only for [Zetaflix - Genre Module] widget.') ,
		'before_widget' => '',
		'after_widget' => '',
		'before_title' => '',
		'after_title' => '',
	));
}
add_action('widgets_init', 'widgets_home');

// Home
function sidebar_home()
{
	register_sidebar(array(
		'name' => __z('Sidebar Homepage') ,
		'id' => 'sidebar-home',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_home');

//Watch Page
function sidebar_watch()
{
	register_sidebar(array(
		'name' => __z('Sidebar Watch Page') ,
		'id' => 'sidebar-watch',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
//add_action('widgets_init', 'sidebar_watch');

function sidebar_archive()
{
	register_sidebar(array(
		'name' => __z('Sidebar Archive') ,
		'id' => 'sidebar-archive',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_archive');

// Movies
function sidebar_movies()
{
	register_sidebar(array(
		'name' => __z('Sidebar Movies') ,
		'id' => 'sidebar-movies',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_movies');
// TVShows
function sidebar_tvshows()
{
	register_sidebar(array(
		'name' => __z('Sidebar TVShows') ,
		'id' => 'sidebar-tvshows',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_tvshows');
// Seasons
function sidebar_seasons()
{
	register_sidebar(array(
		'name' => __z('Sidebar Seasons') ,
		'id' => 'sidebar-seasons',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_seasons');
// Episodes
function sidebar_episodes()
{
	register_sidebar(array(
		'name' => __z('Sidebar Episodes') ,
		'id' => 'sidebar-episodes',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_episodes');

// Posts
function sidebar_posts()
{
	register_sidebar(array(
		'name' => __z('Sidebar Blog Page') ,
		'id' => 'sidebar-blog-single',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_posts');

// Posts Archive
function sidebar_posts_archive()
{
	register_sidebar(array(
		'name' => __z('Sidebar Blog Archive') ,
		'id' => 'sidebar-blog-archive',
		'description' => __z('Add widgets here to appear in your sidebar.') ,
		'before_widget' => '<div class="sidebar-module">',
		'after_widget' => '</div>',
		'before_title' => '<div class="sidebar-title"><span>',
		'after_title' => '</span></div>',
	));
}
add_action('widgets_init', 'sidebar_posts_archive');



function zt_widgets_default($location = ''){
	get_template_part ('inc/widgets/default/widget_' . $location);
}

// Registrar Widgets
function zt_widgets()
{
	register_widget('ZT_Widget_home');
	register_widget('ZT_Widget_social');
	register_widget('ZT_Widget_related');
  	register_widget('ZT_Widget_random');
	register_widget('ZT_Widget_genres');
	register_widget('ZT_Widget_popular');
	register_widget('ZT_Widget_blog_tags');
	register_widget('ZT_Widget_blog_category');
	register_widget('ZT_Widget_blog_recent');
	register_widget('ZT_Widget_blog_related');
	register_widget('ZT_Widget_mreleases');
}
add_action('widgets_init', 'zt_widgets');

get_template_part ('inc/widgets/content_widget_home');
get_template_part ('inc/widgets/content_widget_related');
get_template_part ('inc/widgets/content_widget_random');
get_template_part ('inc/widgets/content_widget_views');
get_template_part ('inc/widgets/content_widget_social');
get_template_part ('inc/widgets/content_widget_tags');
get_template_part ('inc/widgets/content_widget_blog_category');
get_template_part ('inc/widgets/content_widget_blog_recent');
get_template_part ('inc/widgets/content_widget_blog_related');
get_template_part ('inc/widgets/content_widget_popular');
get_template_part ('inc/widgets/content_widget_meta_genres');
get_template_part ('inc/widgets/content_widget_meta_releases');