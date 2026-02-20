<?php
/**
 * Template part for displaying results in search pages
 *
 * @link https://developer.wordpress.org/themes/basics/template-hierarchy/
 *
 * @package fmovie
 */

?>

<div class="container mt-5">
    <section class="bl">
        <div class="heading"> 
            <h2><?php echo get_search_query(); ?></h2> 
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
			</div>  
		</div><!-- #content -->
	</section><!-- #section -->
</div><!-- #container -->
