<?php
/*
* -------------------------------------------------------------------------------------
* @author: Zetathemes
* @author URI: https://zetathemes.com/
* @aopyright: (c) 2023 Zetathemes. All rights reserved
* -------------------------------------------------------------------------------------
*
* @since 1.0.0
*
*/
// Sidebar
	$blog_heading = zeta_get_option('blog_heading', 'image');
?>
<?php zeta_breadcrumb( $post->ID, 'blog', __z('Blog')); ?>
<main>
      <div class="blog-post">
	  <?php if (have_posts()) :while (have_posts()) : the_post(); ?>
	  <?php if($blog_heading != 'image'){?>
        <div class="post-title">
          <h3><?php the_title();?></h3>
        </div>
        <div class="post-cats">
			<?php the_category(' '); ?>
        </div>
		<?php }?>
        <div class="post-head">
		<div class="post-user"><i class="fa-solid fa-user"></i> <?php the_author(); ?></div> 
		<div class="post-date"><i class="fa-solid fa-calendar-days"></i> <?php zeta_post_date('F j, Y'); ?></div>
		<?php if($views = zeta_get_postmeta('zt_views_count')) { echo '<div class="post-views"><i class="fa-regular fa-eye"></i> '. $views .'</div>'; } ?>
		</div>
        
        <div class="post-content">
          <?php the_content(); ?>
        </div>
        <div class="post-foot">
          <?php zeta_social_share($post->ID); ?>
           <?php if (has_tag()) { the_tags( '<div class="post-tags">
           <i class="fa-solid fa-tags"></i> '.__z('Tags').':</span> ' , ', ', '</div>'); }?>
        </div>
		<?php endwhile; endif;?>
      </div>
<?php get_template_part('inc/parts/comments'); ?>
      <div class="clearfix"></div>
    </main>

<?php get_template_part('inc/parts/sidebar', null, array('ptyp' => 'blog')); ?>
