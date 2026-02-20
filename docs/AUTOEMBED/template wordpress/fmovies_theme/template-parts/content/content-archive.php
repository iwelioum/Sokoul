<?php
/**
 * Template part for displaying archive
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>

<div class="container mt-5">
    <section class="bl">
        <div class="heading"> 
            <h1><?php  
                        if (is_search()){
                            echo 'Search: ' . esc_html( $_GET['s'] );
                            } else {  
                            the_archive_title();
                        }
                    ?></h1> 
            <div id="filter-toggler"><i class="fa fa-filter"></i>  <?php echo esc_html__( 'Filter', 'fmovie' ) ?></div> 
            <div class="clearfix"></div> 
        </div><!-- #heading -->
        <div class="content">
            <div class="filmlist md active">
                    <?php
                        while ( have_posts() ) : the_post();
                        get_template_part( 'template-parts/content/content', 'loop' );
                        endwhile; 
                        wp_reset_postdata();
                    ?>		
                
                <div class="clearfix"></div>
            </div>
            <div class="pagenav">
                <?php fmovie_pagination(); ?>
            </div><!-- #pagenav -->  
        </div><!-- #content -->
    </section><!-- #section -->
</div><!-- #container -->



                
                

