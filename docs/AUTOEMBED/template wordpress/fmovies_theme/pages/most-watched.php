<?php
/**
 * Template Name: Most Watched
 * 
 * A custom page template for Most Watched.
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

get_header(); ?>
<div class="container mt-5">
    <section class="bl">
        <div class="heading"> 
            <?php the_title( '<h1>', '</h1>' ); ?> 
            <div class="clearfix"></div> 
        </div><!-- #heading -->
        <div class="content">
            <div class="filmlist md active">
                <?php 
                    if (have_posts()) : 
                    $paged = (get_query_var('paged')) ? get_query_var('paged') : 1; 
                    query_posts( array(
                       'post_type' => 'post',
                       'post_status' => 'publish',
                       'meta_key' => 'end_time',
                       'meta_compare' =>'>=',
                       'meta_value'=>time(),
                       'meta_key' => 'post_views_count',
                       'post__not_in' => get_option( 'sticky_posts' ),
                       'orderby' => 'meta_value_num', 
                       'order' => 'DESC', 
                       'paged' => $paged
                    ));
                    while ( have_posts() ) : the_post(); 
                      get_template_part( 'template-parts/content/content', 'loop' ); 
                    endwhile; 
                    else : endif; 
                ?>
                
                <div class="clearfix"></div>
            </div>
            <div class="pagenav">
                <?php fmovie_pagination(); ?>
            </div><!-- #pagenav -->
        </div><!-- #content -->
    </section><!-- #section -->
</div><!-- #container -->
<?php get_footer(); ?>