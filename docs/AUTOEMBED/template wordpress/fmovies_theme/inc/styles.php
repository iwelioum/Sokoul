<?php
/**
 * Styles and scripts registration and enqueuing
 *
 * @package fmovie
 */


//scripts and styles.
add_action( 'wp_enqueue_scripts', 'fmovie_scripts' );
function fmovie_scripts() {
    $color_style = get_option('admin_color_style');
    $fmovie_comments = get_option('admin_comments');
	wp_register_style( 'style', get_stylesheet_uri(), array(), wp_get_theme()->get( 'Version' ) );

	wp_register_script( 'lazyload', 'https://cdn.jsdelivr.net/npm/lazyload@2.0.0-rc.2/lazyload.js', array('jquery'), '2.0.0', true);
    wp_register_script( 'bootstrap', 'https://cdn.jsdelivr.net/npm/bootstrap@4.5.3/dist/js/bootstrap.bundle.min.js', array('jquery'), '4.5.3', true);
	wp_register_script( 'swiper', 'https://cdn.jsdelivr.net/npm/swiper@5.4.5/js/swiper.min.js', array('jquery'), '5.4.5', true);
	wp_register_script( 'tooltipster', 'https://cdn.jsdelivr.net/npm/tooltipster@4.2.8/dist/js/tooltipster.bundle.min.js', array('jquery'), '4.2.8', true);
	wp_register_script( 'script', get_template_directory_uri() . '/assets/js/script.js', array('jquery'), wp_get_theme()->get( 'Version' ), true);
	if ( is_single() ) {
	wp_register_script( 'comments', get_template_directory_uri() . '/assets/js/min/comments.min.js', array('jquery'), wp_get_theme()->get( 'Version' ), true);
    }
    
    //wp_enqueue_style( 'color' );
    wp_enqueue_style( 'style' );
    
	wp_enqueue_script( 'lazyload' );
    wp_enqueue_script( 'bootstrap' );
	wp_enqueue_script( 'swiper' );
	wp_enqueue_script( 'tooltipster' );
	wp_enqueue_script( 'script' );
	if ( is_single() ) {
    if ($fmovie_comments == 1) {
	wp_enqueue_script( 'comments' );
     }
    }
    if ( is_singular() && comments_open() && get_option( 'thread_comments' ) ) {
    if ($fmovie_comments == 1) { } else { 
    wp_enqueue_script( 'comment-reply' );
    }
    }
	}

//dns-prefetch
add_action( 'wp_head', 'fmovie_prefetch', 2 );
function fmovie_prefetch() {
?>

<link rel="dns-prefetch" href="//www.googletagmanager.com">
<link rel="dns-prefetch" href="//www.gstatic.com">
<link rel="dns-prefetch" href="//fonts.gstatic.com">
<link rel="dns-prefetch" href="//fonts.googleapis.com">
<link rel="dns-prefetch" href="//cdn.jsdelivr.net">
<link rel="dns-prefetch" href="//image.tmdb.org">

<link rel="preconnect" href="//fonts.googleapis.com" crossorigin>
<link rel="preconnect" href="//fonts.gstatic.com" crossorigin>
<link rel="preconnect" href="//www.googletagmanager.com" crossorigin>

<?php }

//async disqus
function make_disqus_async( $tag, $handle, $src )
{
    if ( 'comments' != $handle ) {
        return $tag;
    }

    return str_replace( "<script type='text/javascript'", "<script type='text/javascript' async", $tag );
}
add_filter( 'script_loader_tag', 'make_disqus_async', 10, 3 );

//get header class
function get_header_class( $class = '' ) {
    global $wp_query;
 
    $classes = array();
 
    if ( is_rtl() ) {
        $classes[] = 'rtl';
    }
 
    if ( is_front_page() ) {
        $classes[] = 'home';
    }
    if ( is_home() ) {
        $classes[] = 'blog';
    }
    if ( is_privacy_policy() ) {
        $classes[] = 'privacy-policy';
    }
    if ( is_archive() ) {
        $classes[] = 'archive';
    }

    if ( is_search() ) {
        $classes[] = 'search';
        
    }
    if ( is_paged() ) {
        $classes[] = 'paged';
    }
    if ( is_attachment() ) {
        $classes[] = 'attachment';
    }
    if ( is_404() ) {
        $classes[] = 'error404';
    }
 
    if ( is_singular() ) {
        $post_id   = $wp_query->get_queried_object_id();
        $post      = $wp_query->get_queried_object();
        $post_type = $post->post_type;
 
        if ( is_page_template() ) {
            $classes[] = "{$post_type}-template";
 
            $template_slug  = get_page_template_slug( $post_id );
            $template_parts = explode( '/', $template_slug );
 
            foreach ( $template_parts as $part ) {
                $classes[] = "{$post_type}-template-" . sanitize_html_class( str_replace( array( '.', '/' ), '-', basename( $part, '.php' ) ) );
            }
            $classes[] = "{$post_type}-template-" . sanitize_html_class( str_replace( '.', '-', $template_slug ) );
        } else {
            $classes[] = "{$post_type}-template-default";
        }
 
        if ( is_single() ) {
            $classes[] = 'single';

        }
 
        if ( is_attachment() ) {

        } elseif ( is_page() ) {

        
    } elseif ( is_page('filters') ) {
$classes[] = 'archive';
        
    } elseif ( is_archive() ) {
        
            
            
        } elseif ( is_author() ) {

            $classes[] = 'author';
        } elseif ( is_category() ) {

            $classes[] = 'category';

            
        } elseif ( is_tag() ) {

            $classes[] = 'archive';
            
        } elseif ( is_tax() ) {
           $classes[] = 'archive';
        }
    }
 
    if ( is_user_logged_in() ) {
        $classes[] = 'logged-in';
    }
 
    if ( is_admin_bar_showing() ) {
        $classes[] = 'admin-bar';
        $classes[] = 'no-customize-support';
    }
 
    if ( current_theme_supports( 'custom-background' )
        && ( get_background_color() !== get_theme_support( 'custom-background', 'default-color' ) || get_background_image() ) ) {
        $classes[] = 'custom-background';
    }
 
    if ( has_custom_logo() ) {
        $classes[] = 'wp-custom-logo';
    }
 
    if ( ! empty( $class ) ) {
        if ( ! is_array( $class ) ) {
            $class = preg_split( '#\s+#', $class );
        }
        $classes = array_merge( $classes, $class );
    } else {

        $class = array();
    }
 
    $classes = array_map( 'esc_attr', $classes );
 
    $classes = apply_filters( 'header_class', $classes, $class );
 
    return array_unique( $classes );
}

//header class
function header_class( $classes = '' ) {
    echo 'class="' . esc_attr( implode( ' ', get_header_class( $classes ) ) ) . '"';
}