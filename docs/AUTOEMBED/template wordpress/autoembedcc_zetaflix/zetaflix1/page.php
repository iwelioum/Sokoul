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
get_header();
$page_heading = zeta_get_option('blog_heading', 'image');

?>
<main>
      <div class="blog-post page">
	  <?php if (have_posts()) :while (have_posts()) : the_post(); ?>
	  <?php if($page_heading != 'image'){?>
        <div class="post-title">
          <h3><?php the_title();?></h3>
        </div>
		<?php }?>
        <div class="post-content">
          <?php the_content(); ?>
        </div>
		<?php endwhile; endif;?>
      </div>

<?php if(zeta_get_option('commentspage') == true) { get_template_part('inc/parts/comments'); } ?>
      <div class="clearfix"></div>
    </main>



<?php get_footer(); ?>
