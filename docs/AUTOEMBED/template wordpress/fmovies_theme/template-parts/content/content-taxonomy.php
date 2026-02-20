<?php
/**
 * Template part for displaying tax
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>

<div class="container mt-5">
    <section class="bl">
        <div class="heading"> 
            <h1><?php single_term_title(); ?></h1> 
            <div class="clearfix"></div> 
        </div>
        <?php get_template_part('template-parts/content/content', 'filters'); ?>
        <div class="content">
            <div class="filmlist md active">
                <?php
                    global $wp_query;
                    $current_taxonomy = get_queried_object();
                    $parent_id = $current_taxonomy->parent;
                    $this_tax_slug = $current_taxonomy->slug;
                    $paged = (get_query_var('paged')) ? get_query_var('paged') : 1;
                    $args = array(
                    'orderby' => 'post_date',
                    'order' => 'DESC',
                    'post_type' => 'post',
                    'post_status' => 'publish',
                    'posts_per_page' => get_option('posts_per_page') ,
                    'paged' => $paged,
                    'tax_query' => array(
                    array(
                    'taxonomy' => $taxonomy,
                    'field' => 'id',
                    'terms' => $current_taxonomy->term_id,
                    ) ,
                    ) ,
                    );
                    
                    //if(!isset($_GET['s'])) { $_GET['s'] = ''; }
                    if (!isset($_GET['order']))
                    {
                        $_GET['order'] = '';
                    }
                    
                    if ($_GET['order'] == 'Views')
                    {
                        $args['meta_key'] = 'post_views_count';
                        $args['orderby'] = 'meta_value_num';
                    }
                    if ($_GET['order'] == 'Rating')
                    {
                        $args['meta_key'] = 'vote_average';
                        $args['orderby'] = 'meta_value_num';
                    }
                    if ($_GET['order'] == 'Year')
                    {
                        $args['meta_key'] = 'release_date';
                        $args['orderby'] = 'meta_value_num';
                    }
                    if ($_GET['order'] == 'years-asc')
                    {
                        $args['meta_key'] = 'release_date';
                        $args['orderby'] = 'meta_value_num';
                        $args['order'] = 'ASC';
                    }
                    if ($_GET['order'] == 'Title')
                    {
                        $args['orderby'] = 'title';
                        $args['order'] = 'ASC';
                    }
                    if ($_GET['order'] == 'title-desc')
                    {
                        $args['orderby'] = 'title';
                    }
                    if ($_GET['order'] == 'Latest')
                    {
                        $args['orderby'] = 'post_date';
                    }
                    if ($_GET['order'] == 'Random')
                    {
                        $args['orderby'] = 'rand';
                    }
                    if ($_GET['order'] == 'date-asc')
                    {
                        $args['orderby'] = 'post_date';
                        $args['order'] = 'ASC';
                    }
                    $the_query = new WP_Query($args);
                    while ($the_query->have_posts()):
                    $the_query->the_post();
                    get_template_part('template-parts/content/content', 'loop');
                    endwhile;
                    wp_reset_query();
                    wp_reset_postdata();
                ?>
                
                <div class="clearfix"></div>
            </div>
            <div class="pagenav">
                <?php fmovie_pagination(); ?>
            </div>  
        </div>
    </section><!-- #section -->
</div><!-- #container -->
