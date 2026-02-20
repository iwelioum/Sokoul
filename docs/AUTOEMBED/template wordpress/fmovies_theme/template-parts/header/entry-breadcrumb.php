<?php
/**
 * Template part for breadcrumb
 *
 * @package fmovie
 */
 
// cat movies
$category_movies = get_cat_ID( 'Movies' ); 
$category_link_movies = get_category_link( $category_movies );
// cat series
$category_id = get_cat_ID( 'TV Series' ); 
$category_link = get_category_link( $category_id );
// breadcrumb_link
if ( is_post_template( 'tv.php' ) ) {
$breadcrumb_link = esc_url( $category_link );
} else {
$breadcrumb_link = esc_url( $category_link_movies );
}
?>

<div class="nav">
    <div class="container">
        <ol class="breadcrumb" itemscope itemtype="http://schema.org/BreadcrumbList">
            <li class="breadcrumb-item" itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem"> 
                <a itemprop="item" href="<?php echo esc_url( home_url( '/' ) ); ?>" title="<?php echo esc_attr__( 'Watch ', 'fmovie' ) ?><?php BreadcrumbType() ?><?php echo esc_html__( ' online', 'fmovie' ) ?>">
                    <span itemprop="name"><?php echo esc_html__( 'Home', 'fmovie' ) ?></span>
                </a>
                <meta itemprop="position" content="1" />
            </li>
            <li class="breadcrumb-item" itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem"> 
                <a itemprop="item" href="<?php echo $breadcrumb_link; ?>" title="<?php BreadcrumbType() ?>">
                    <span itemprop="name"><?php BreadcrumbType() ?></span>
                </a>
                <meta itemprop="position" content="2" />
            </li>
            <li class="breadcrumb-item active text-truncate" itemprop="itemListElement" itemscope itemtype="http://schema.org/ListItem"> 
                <a itemprop="item" href="<?php the_permalink(); ?>" title="<?php the_title(); ?>">
                    <span itemprop="name"><?php the_title(); ?></span>
                </a>
                <meta itemprop="position" content="3" />
            </li>
        </ol>
    </div>
</div>