<?php
/*
 * Theme setup functions. Theme initialization, add_theme_support(), widgets, define & more.
 *
 * @package fmovie
 */

//Tell WordPress to run fmovie_setup() when the 'after_setup_theme' hook is run.
if ( ! function_exists( 'fmovie_setup' ) ) {

function fmovie_setup() {
//add_theme_support
add_theme_support ( 'title-tag' );
add_theme_support ( 'responsive-embeds' );
add_theme_support ( 'align-wide' );

add_theme_support ( 'post-thumbnails' );

add_image_size( 'cover', 1920, 1080, true );


add_theme_support( 'html5', array(
'search-form',
//'comment-form',
'comment-list',
'gallery',
'caption',
'style',
'script',
) );

add_theme_support ( 'automatic-feed-links' );
add_theme_support ( 'customize-selective-refresh-widgets' );

// filter
add_filter( 'use_block_editor_for_post', '__return_false');
add_filter( 'auto_update_theme', '__return_false' );
add_filter( 'rss_widget_feed_link', '__return_false' );
add_filter( 'rank_math/admin/disable_primary_term', '__return_true');
add_filter( 'wpseo_next_rel_link', '__return_false' );
add_filter( 'wpseo_prev_rel_link', '__return_false' );
add_editor_style( 'assets/css/editor.css' );
remove_action( 'set_comment_cookies', 'wp_set_comment_cookies' );
//logo
$logo_width  = 138;
$logo_height = 40;
add_theme_support(
'custom-logo',
array(
'height'       => $logo_height,
'width'        => $logo_width,
'flex-width'   => true,
'flex-height'  => true,
'unlink-homepage-logo' => false, 
)
);
}
}
add_action( 'after_setup_theme', 'fmovie_setup' );

//define
$color_style = get_option('admin_color_style');
define	('disqus_id', 'movieapp-1'); 
if ($color_style == 'light') {
define	('placeholder', "data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 500 750'%3E%3C/svg%3E"); 
} else {
define	('placeholder', 'data:image/webp;base64,UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAgA0JaQAA3AA/vv9UAA='); 
} 
define	('placeholder_slider', 'data:image/webp;base64,UklGRiQAAABXRUJQVlA4IBgAAAAwAQCdASoBAAEAAgA0JaQAA3AA/vv9UAA='); 

//flush_rewrite
function fmovie_c() {
	flush_rewrite_rules();
}
add_action('after_switch_theme', 'fmovie_c');

//wp_body_open
if (!function_exists('wp_body_open')) {

    function wp_body_open()
    {

        do_action('wp_body_open');
    }
}

//custom post template
if ( ! function_exists( 'is_post_template' ) ) {
function is_post_template($template = '') {
	if (!is_single()) {
		return false;
	}

	global $wp_query;

	$post = $wp_query->get_queried_object();
	$post_template = get_post_meta( $post->ID, 'custom_post_template', true );

	if ( empty( $template ) ) {
		if (!empty( $post_template ) ) {
			return true;
		}
	} elseif ( $template == $post_template) {
		return true;
	}

	return false;
}
}

//create tv series
wp_update_term(1, 'category', array(
    'name' => 'TV Series',
    'slug' => 'tv-series', 
    'description' => ''
));

//create movies
function _CreateMovies(){
$my_cat = array(
    'cat_name' => 'Movies', 
    'category_description' => '',
    'category_nicename' => 'movies',
    'category_parent' => ''
	);

wp_insert_category($my_cat);
}
add_action('admin_init','_CreateMovies');

//create slider
function _CreateSlider(){
$my_slider = array('cat_name' => 'Slider', 
    'category_description' => '',
    'category_nicename' => 'slider',
    'category_parent' => '');

wp_insert_category($my_slider);
}
add_action('admin_init','_CreateSlider');

//create recommended
function _CreateRecommended(){
$my_recommended = array('cat_name' => 'Recommended', 
    'category_description' => '',
    'category_nicename' => 'recommended',
    'category_parent' => '');

wp_insert_category($my_recommended);
}
add_action('admin_init','_CreateRecommended');

//hide specific category
add_filter( 'get_the_categories', 'remove_category_link' );
function remove_category_link( $categories ) {

    if ( is_admin() ) 
        return $categories;

    $remove = array();

    foreach ( $categories as $category ) {

	if ( $category->name == "Movies" ) continue;
	if ( $category->name == "TV Series" ) continue;
	if ( $category->name == "Slider" ) continue;
	if ( $category->name == "Recommended" ) continue;
    $remove[] = $category;
    }
    return $remove;
}

//widgets & sidebar
function fmovie_widgets_init() {

	register_sidebar(
		array(
			'name'          => esc_html__( 'Footer Left', 'fmovie' ),
			'id'            => 'sidebar-1',
			'description'   => esc_html__( 'Add widgets here to appear in your left footer.', 'fmovie' ),
			'before_widget' => '<div id="%1$s" class="bl footer-navigation widget %2$s">',
			'after_widget'  => '</div>',
			'before_title'  => '<div class="heading">',
			'after_title'   => '</div>',
		)
	);
	register_sidebar(
		array(
			'name'          => esc_html__( 'Footer Center', 'fmovie' ),
			'id'            => 'sidebar-2',
			'description'   => esc_html__( 'Add widgets here to appear in your center footer.', 'fmovie' ),
			'before_widget' => '<div id="%1$s" class="bl footer-navigation widget %2$s">',
			'after_widget'  => '</div>',
			'before_title'  => '<div class="heading">',
			'after_title'   => '</div>',
		)
	);
	register_sidebar(
		array(
			'name'          => esc_html__( 'Footer Right', 'fmovie' ),
			'id'            => 'sidebar-3',
			'description'   => esc_html__( 'Add widgets here to appear in your right footer.', 'fmovie' ),
			'before_widget' => '<div id="%1$s" class="bl footer-navigation widget %2$s">',
			'after_widget'  => '</div>',
			'before_title'  => '<div class="heading">',
			'after_title'   => '</div>',
		)
	);
}
add_action( 'widgets_init', 'fmovie_widgets_init' );

//yoast change type
//add_filter( 'wpseo_opengraph_type', 'yoast_change_opengraph_type', 10, 1 );
//function yoast_change_opengraph_type( $type ) {

  //if ( is_single() ) {
    //return 'video.movie';
  //} else {
    //return $type;
  //}
//}

//remove wp block 
function fmovie_remove_wp_block_library_css(){
 wp_dequeue_style( 'wp-block-library' );
 wp_dequeue_style( 'wp-block-library-theme' );
}
add_action( 'wp_enqueue_scripts', 'fmovie_remove_wp_block_library_css' );

//global_styles 
function fmovie_remove_global_css() {
remove_action( 'wp_enqueue_scripts', 'wp_enqueue_global_styles' );
 remove_action( 'wp_body_open', 'wp_global_styles_render_svg_filters' );
}
add_action( 'init', 'fmovie_remove_global_css' );

//posts_per_page 
function fmovie_posts_per_page($query) {
	$user_posts_per_page = get_option('posts_per_page');
    if ($user_posts_per_page < 24) {
		update_option('posts_per_page', 24);		
	}
}
add_action('pre_get_posts', 'fmovie_posts_per_page');

//wp_default_editor
add_filter( 'wp_default_editor',
function( $default_editor ) {
if ( current_user_can( 'editor' ) || current_user_can( 'author' ) ) {
$default_editor = 'html';
  }
  return $default_editor;
}
);

//limit number of tags
add_filter('term_links-post_tag','limit_to_five_tags');
function limit_to_five_tags($terms) {
return array_slice($terms,0,5,true);
}

//hide categories
add_filter('get_the_terms', 'hide_categories_terms', 10, 3);
function hide_categories_terms($terms, $post_id, $taxonomy){
    $slug = 'Recommended';
    $cat = get_category_by_slug($slug); 
    $catID = $cat->term_id;

    $excludeIDs = array(1, 2, 3, $catID);
    $exclude = array();
    foreach ($excludeIDs as $id) {
        $exclude[] = get_term_by('id', $id, 'category');
    }
    if (!is_admin()) {
        foreach($terms as $key => $term){
            if($term->taxonomy == "category"){
                foreach ($exclude as $exKey => $exTerm) {
                    if($term->term_id == $exTerm->term_id) unset($terms[$key]);
                }
            }
        }
    }

    return $terms;
}

//redirect not found
function fmovie_search_template( $template ) {
    if( ! have_posts() ) {
       $template = locate_template( array( '404.php' ) );
    }
    return $template;
}
add_filter( 'search_template', 'fmovie_search_template' );

//excerpt
function fmovie_excerpt($charlength) {
	$excerpt = get_the_excerpt();
	$charlength++;
	if(mb_strlen($excerpt) > $charlength) {
		$subex   = mb_substr( $excerpt, 0, $charlength - 5 );
		$exwords = explode( ' ', $subex );
		$excut   = - (mb_strlen($exwords[ count($exwords) - 1]));
		if($excut < 0) {
			echo mb_substr($subex, 0, $excut);
		} else {
			echo $subex;
		}
		echo '...';
	} else {
		echo $excerpt;
	}
}

//text-dark to p tag
add_filter('the_content', 'fmovie_the_content', 10, 1);
function fmovie_the_content($content = null)

{
     if (is_page()) {
        
        if (null === $content)
             return $content;
         return str_replace('<p>', '<p>', $content);
         } else {
        
        if (null === $content)
             return $content;
         return str_replace('<p>', '<p>', $content);
         } 
    } 

//Slice crazy long div outputs
function category_id_class($classes) {
    global $post;
    foreach((get_the_category($post->ID)) as $category)
        $classes[] = $category->category_nicename;
        return array_slice($classes, 0,7);
}
add_filter('post_class', 'category_id_class');

//archive_title
function fmovie_archive_title( $title ) {
    if ( is_category() ) {
        $title = single_cat_title( '', false );
    } elseif ( is_tag() ) {
        $title = single_tag_title( '', false );
    } elseif ( is_post_type_archive() ) {
        $title = post_type_archive_title( '', false );
    } elseif ( is_tax() ) {
        $title = single_term_title( '', false );
    }
  
    return $title;
}
 
add_filter( 'get_the_archive_title', 'fmovie_archive_title' );

//create pages
if (isset($_GET['activated']) && is_admin()){
    add_action('init', 'create_initial_pages');
}
function create_initial_pages() {

    $pages = array( 
        'Favorites' => array(
            'Favorites Content'=>'pages/favorites.php'),

        'Top IMDB' => array(
            'Top IMDB Content'=>'pages/top.php'),
			
        'Most Watched' => array(
            'Most Watched Content'=>'pages/most-watched.php'),
			
        'FAQs' => array(
            'FAQ Content'=>'pages/faq.php'),
    );

    foreach($pages as $page_url_title => $page_meta) {

        $id = get_page_by_title($page_url_title);

        foreach ($page_meta as $page_content=>$page_template){

            $page = array(
                'post_type'   => 'page',
                'post_title'  => $page_url_title,
                'post_name'   => $page_url_title,
                'post_status' => 'publish',
                'post_content' => $page_content,
				'ping_status'    => 'closed',
				'comment_status' => 'closed',
                'post_author' => 1,
                'post_parent' => ''
            );

            if(!isset($id->ID)){
                $new_page_id = wp_insert_post($page);
                if(!empty($page_template)){
                    update_post_meta($new_page_id, '_wp_page_template', $page_template);
                }
            }
        }
    }
}

//permalink_structure
add_action( 'init', function() {
    global $wp_rewrite;
    $wp_rewrite->set_permalink_structure( '/%postname%/' );
    flush_rewrite_rules();
} );

// tmdb yoast image
function default_og_image ($image)
{
     global $post;
     if (is_singular('post')) {
        if (!$image -> has_images()) {
            $image -> add_image('default'); 
             } 
        } 
    } 
add_action('wpseo_add_opengraph_additional_images', 'default_og_image');

// set the default share image
function default_share_image ($image)
{
     global $post;
     if (is_singular('post')) {
        if (!$image || $image === 'default') { 
		
             $featured_img_url = get_the_post_thumbnail_url(get_the_ID(), 'full');
             $poster_path = get_post_meta($post -> ID, 'poster_path', true);
             $full_poster_path = 'https://image.tmdb.org/t/p/w600_and_h900_bestv2' . $poster_path;
             $image = $full_poster_path;
			 
             if ($poster_path == '') {
                if (has_post_thumbnail()) {
                    $image = esc_url($featured_img_url);
                     } else {
                    
                    $image = esc_url('https://via.placeholder.com/600x900?text=No+Poster&000.jpg');
					
                     } 
                } else {
                
                $image = $full_poster_path;

                } 
        } 
        return $image;
	  } 
} 
add_action('wpseo_twitter_image', 'default_share_image');
add_action('wpseo_opengraph_image', 'default_share_image');

function mytheme_move_jquery_to_footer() {
    wp_scripts()->add_data( 'jquery', 'group', 1 );
    wp_scripts()->add_data( 'jquery-core', 'group', 1 );
    wp_scripts()->add_data( 'jquery-migrate', 'group', 1 );
}
add_action( 'wp_enqueue_scripts', 'mytheme_move_jquery_to_footer' );

//new content to columns 
function post_add_new_columns($columns)  {
        unset($columns['author']);
        unset($columns['tags']);
		unset($columns['comments']);
		unset($columns['date']);
		unset($columns['wprc_post_reports']);
		$columns['Year'] = 'Year';
		$columns['categories'] = 'Genre';
		$columns['Poster'] = 'Poster';
		$columns['Type'] = 'Type';
		$columns['Rating'] = 'Rating';
	return $columns;
}
add_filter('manage_edit-post_columns', 'post_add_new_columns');

//manage content to columns 
function post_manage_columns($column_name, $id) {
	global $post;
	switch ($column_name) {
		case 'Poster':

			$poster_path = esc_html(get_post_meta($post -> ID, 'poster_path', true));
	        $poster92 = 'https://image.tmdb.org/t/p/w92'.$poster_path;
			$featured_img_url = get_the_post_thumbnail_url(get_the_ID(), 'full');
			$placeholder = esc_url( get_template_directory_uri() ).'/assets/img/placeholder.png';
            if ( has_post_thumbnail() ) {
			echo '<img width="53" height="80" src="'.esc_url($featured_img_url).'" style="border-radius:5px;" />';
            } else {
            if ($poster_path == '') {
			echo '<img width="53" height="80" src="'.$placeholder.'" style="border-radius:5px;" />';
            } else {
		    echo '<img width="53" height="80" src="'.esc_url($poster92).'" style="border-radius:5px;" />';
            }
            }
			break;
		case 'Type':
		    if ( in_category('tv-series') ) {  _e('TV', 'fmovie');  } else {  _e('MOVIE', 'fmovie'); } 
			break;
		case 'Year':
		    $years = get_post_meta( $post->ID , 'release_date' , true );
			if( $years == "" ){ 
			echo 'Unable to get year'; 
			} else {
		    echo $years;
			}
			break;
		case 'Rating':
		    $vote_average = esc_html(get_post_meta($post -> ID, 'vote_average', true));
			$vote_average = substr($vote_average, 0, 3);
			if ($vote_average != '') {
            echo '<i style="line-height:-1;color: #f7e330;margin-right:5px;" class="dashicons dashicons-star-filled"></i>'.$vote_average;
            } 
			break;
	} 
}
add_action('manage_post_posts_custom_column', 'post_manage_columns', 10, 2);

function post_columns_sortable($columns) {
	$custom = array(
	    'Year' => 'Year',
		'Rating' => 'Rating',
	);
	return wp_parse_args($custom, $columns);
}
add_filter('manage_edit-post_sortable_columns', 'post_columns_sortable');

add_action('pre_get_posts', function($query) {
    if (!is_admin()) {
        return;
    }
    $orderby = $query->get('orderby');
    if ($orderby == 'Year') {
        $query->set('meta_key', 'release_date');
        $query->set('orderby', 'meta_value_num');
    }
    if ($orderby == 'Rating') {
        $query->set('meta_key', 'vote_average');
        $query->set('orderby', 'meta_value_num');
    }
});

add_action( 'admin_bar_menu', 'customize_admin_bar', 999);
function customize_admin_bar()
{
    global $wp_admin_bar;
	if ( !is_super_admin() || !is_admin_bar_showing() )
    return;
	
    $wp_admin_bar->add_menu( array(
	'id' => 'wp-admin-bar-fmovie',
        'title' => 'FMovies',
        'href' => admin_url('admin.php?page=admin-main'),
    ) );
 
    $wp_admin_bar->add_menu( array(
	'id' => 'fmovie-general',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'General',
        'href' => admin_url('admin.php?page=admin-main'),
    ) );
    $wp_admin_bar->add_menu( array(
        'id' => 'fmovie-home',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Home',
        'href' => admin_url('admin.php?page=admin-home'),
    ) );
    $wp_admin_bar->add_menu( array(
        'id' => 'fmovie-branding',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Branding',
        'href' => admin_url('admin.php?page=admin-branding'),
    ) );

    $wp_admin_bar->add_menu( array(
        'id' => 'fmovie-translate',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Translate',
        'href' => admin_url('admin.php?page=admin-translate'),
    ) );
	
    $wp_admin_bar->add_menu( array(
        'id' => 'fmovie-comments',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Comments',
        'href' => admin_url('admin.php?page=admin-comments'),
    ) );
	$wp_admin_bar->add_menu( array(
        'id' => 'fmovie-player',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Player',
        'href' => admin_url('admin.php?page=admin-player'),
    ) );
    $wp_admin_bar->add_menu( array(
        'id' => 'fmovie-reset',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Reset',
        'href' => admin_url('admin.php?page=admin-reset'),
    ) );
    $wp_admin_bar->add_menu( array(
        'id' => 'fmovie-movie',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Import Movies',
        'href' => admin_url('admin.php?page=moviewp_movie'),
    ) );
    $wp_admin_bar->add_menu( array(
        'id' => 'fmovie-tv',
        'parent' => 'wp-admin-bar-fmovie',
        'title' => 'Import TV',
        'href' => admin_url('admin.php?page=moviewp_tv'),
    ) );
}



function fmovies_exclude_pages_from_search_results( $query ) {
    if ( $query->is_main_query() && $query->is_search() && ! is_admin() ) {
        $query->set( 'post_type', array( 'post' ) );
    }    
}
add_action( 'pre_get_posts', 'fmovies_exclude_pages_from_search_results' );


add_filter( 'get_custom_logo', 'fmovie_change_logo_class' );
function fmovie_change_logo_class( $html ) {

    $html = str_replace( 'custom-logo-link', 'navbar-brand', $html );

    return $html;
}

function fmovie_comments($comment, $args, $depth) {
	$GLOBALS['comment'] = $comment; ?>
	<li <?php comment_class(); ?> id="comment-<?php comment_ID() ?>">
	    
		<div class="comment-wrap">
			<div class="comment-img">
				<?php echo get_avatar($comment,$args['avatar_size'],null,null,array('class' => array('img-responsive', 'rounded') )); ?>
			</div>
			<div class="comment-body">
				<h4 class="comment-author"><?php echo get_comment_author_link(); ?></h4>
				<span class="comment-date"><?php printf(__('%1$s at %2$s', 'fmovie'), get_comment_date(),  get_comment_time()) ?></span>
				<?php if ($comment->comment_approved == '0') { ?><em class="awaiting"><i class="fa fa-spinner fa-spin"></i>&nbsp;&nbsp; <?php _e('Comment awaiting approval', 'fmovie'); ?></em><br /><br /><?php } ?>
				<?php comment_text(); ?>
				<span class="comment-reply"> <?php comment_reply_link(array_merge( $args, array('reply_text' => __('<i class="fa fa-reply"></i>', 'fmovie'), 'depth' => $depth, 'max_depth' => $args['max_depth'])), $comment->comment_ID); ?></span>
			</div>
		</div>
<?php }


add_filter('comment_form_default_fields', 'unset_url_field');
function unset_url_field($fields){
    if(isset($fields['url']))
       unset($fields['url']);
       return $fields;
}

function fmovie_customize_comment_form_text_area($arg) {
    $arg['comment_field'] = '<p class="comment-form-comment"><label for="comment">Comment <span class="required">*</span></label><textarea id="comment" name="comment" cols="45" rows="5" aria-required="true"></textarea></p>';
    return $arg;
}

add_filter('comment_form_defaults', 'fmovie_customize_comment_form_text_area');


add_filter( 'comment_form_defaults', 'fmovie_comment_form_defaults' );

function fmovie_comment_form_defaults( $defaults ) {

    $defaults['comment_notes_before'] = '<div class="linea"></div>';

    return $defaults;

}

function fmovie_comment_textarea_placeholder( $args ) {
	$args['comment_field']        = str_replace( 'textarea', 'textarea placeholder="Comment"', $args['comment_field'] );
	return $args;
}
add_filter( 'comment_form_defaults', 'fmovie_comment_textarea_placeholder' );


function fmovie_comment_form_fields( $fields ) {
	foreach( $fields as &$field ) {
		$field = str_replace( 'id="author"', 'id="author" placeholder="Name"', $field );
		$field = str_replace( 'id="email"', 'id="email" placeholder="E-mail"', $field );
		//$field = str_replace( 'id="url"', 'id="url" placeholder="website"', $field );
	}
	return $fields;
}
add_filter( 'comment_form_default_fields', 'fmovie_comment_form_fields' );