<?php
/*
Template Name: ZT - Posts page
*/

// Get Header
get_header();

// Paged Info
$crrpage = get_query_var('paged') ? get_query_var('paged') : 1;
$perpage = zeta_get_option('bperpage','10');
// Recalculate Page
query_posts(array('showposts' => $perpage,'paged' => $crrpage,'order' => 'DESC','post_type' => 'post'));
$sidebar = zeta_get_option('sidebar_position_archives','right');
// End PHP
$cat_slug = get_option('category_base');
$cat_slug = ($cat_slug) ? home_url().'/'.$cat_slug.'/' : home_url().'/category/';
$tag_slug = get_option('tag_base');
$tag_slug = ($tag_slug) ? home_url().'/'.$tag_slug.'/' : home_url().'/tag/';

?>



<main>
         <div class="blog-archive">
		 			<?php if (have_posts()) : while ( have_posts() ) : the_post();
					$cats = get_the_terms($post->ID, 'category');
					$tags = get_the_terms($post->ID, 'post_tag');
			?>
          <div class="blog-post">
            <div class="post-thumb">
			
              <?php echo zetaflix_get_blog_thumb($post->ID);?>
            </div>
            <div class="post-data">
              <div class="post-title">
                <a href="<?php the_permalink(); ?>"><h3><?php the_title();?></h3></a>
              </div>
              <div class="post-meta">
                <a href="#"><i class="fa-solid fa-user"></i> <span class="meta-user"><?php the_author(); ?></span></a>
                <a href="#"><i class="fa-solid fa-calendar-days"></i> <span class="meta-date"><?php zeta_post_date('F j, Y'); ?></span></a>
              </div>
              <div class="post-excerpt">
                <p><?php zt_content_alt('180'); ?></p>
              </div>
              <a href="<?php the_permalink(); ?>" class="post-more"><?php _z('Read More');?></a>
              <div class="post-terms">
			  <?php if($cats && is_array($cats)){?>
                <ul class="terms-cat">
				<li class="term-cat-title"><i class="fa-solid fa-film"></i> <span><?php _z('Category');?>: </span></li>
				<?php  foreach($cats as $cat){?>
                 <li><a href="<?php echo $cat_slug.$cat->slug;?>"><?php echo $cat->name;?></a></li>
				<?php }?>
                </ul>
				<?php }?>
				<?php if($tags && is_array($tags)){?>
                <ul class="terms-tags">
                  <li class="term-tags-title"><i class="fa-solid fa-tags"></i> <span class="term-cat-title"><?php _z('Tags');?> </span></li>
				  <?php  foreach($tags as $tag){?>
				  
                  <li><a href="<?php echo $tag_slug.$tag->slug;?>"><?php echo $tag->name;?></a></li>
                  <?php }?>
                 </ul>
				<?php }?>
              </div>
            </div>


          </div>
         
			<?php endwhile;  ?>
			<?php zeta_pagination();?>
			<?php else: ?>
			<div class="zt-no-post"><?php _z('No posts to show'); ?></div>
			<?php endif; wp_reset_query(); ?>
		 </div>
      <div class="clearfix"></div>
    </main>
	
	

<?php get_template_part('inc/parts/sidebar', null, array('ptype' => 'blog'));?>

<?php get_footer(); ?>
