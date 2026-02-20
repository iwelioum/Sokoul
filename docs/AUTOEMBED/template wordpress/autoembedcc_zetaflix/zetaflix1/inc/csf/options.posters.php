<?php
// Define defaults colors
switch(zeta_get_option('style','default')) {
    case 'dark':
        $mcolor = '#408BEA';
        $bcolor = '#464e5a';
    break;
    case 'fusion':
        $mcolor = '#408BEA';
        $bcolor = '#9facc1';
    break;
    case 'default':
        $mcolor = '#408BEA';
        $bcolor = '#F5F7FA';
    break;
}

/**
 * @since 1.0.0
 * @version 2.0
 */
CSF::createSection(ZETA_OPTIONS,
    array(
        'title'  => __z('Posters'),
        'parent' => 'customize',
        'icon'   => 'fa fa-minus',
        'fields' => array(
            array(
                'id'      => 'play_icon',
                'type'    => 'image_select',
                'title'   => __z('Hover Play Icon'),
                'options' => array(
                    'play1' => ZETA_URI.'/assets/img/play1.png',
                    'play2' => ZETA_URI.'/assets/img/play2.png',
                    'play3' => ZETA_URI.'/assets/img/play3.png',
                    'play4' => ZETA_URI.'/assets/img/play4.png',
                ),
                'default'   => 'play1'
            ),
            array(
                'id'    => 'poster_data',
                'type'  => 'checkbox',
                'title' => __z('Display Data'),
                'subtitle'  => __z('Select contents to display'),
                'options' => array(
                    'title' => __z('Title'),
                    'quality' => __z('Quality'),
                ),
                'default' => array('title')
            ),
            array(
                'type' => 'subheading',
                'content' => __z('Poster Display')
            ),
            array(
                'id' => 'poster_style',
                'type' => 'radio',
                'title' => __z('Poster Style'),
                'default' => 'horizontal',
                'options' => array(
                    'horizontal' => __z('Horizontal'),
                    'vertical'  => __z('Vertical')
                )
            ),
            array(
                'id' => 'poster_source',
                'type' => 'radio',
                'title' => __z('Poster Source'),
                'default' => 'meta',
                'options' => array(
                    'meta' => __z('Post Meta'),
                    'thumb'  => __z('Featured Image')
                )
            ),
            array(
                'id' => 'poster_meta_source',
                'type' => 'radio',
                'title' => __z('Meta Source'),
                'default' => 'zt_backdrop',
                'options' => array(
                    'zt_poster' => __z('Poster Image'),
                    'zt_backdrop'  => __z('Backdrop Image')
                ),
				'dependency' => array('poster_source', '==', 'meta')
            ),
			array(
			  'type'    => 'subheading',
			  'content' => 'Image Sizes',
			),
			array(
			  'type'    => 'submessage',
			  'style'   => 'warning',
			  'content' => 'Settings applicable to post meta fields images"',
			),
			
            array(
                'id'    => 'poster_size',
                'type'  => 'radio',
                'title' => __z('Poster Size'),
                'options' => array(
                    'w92' => __z('92x138'),
                    'w154'  => __z('154x231'),
                    'w185'  => __z('185x278'),
                    'w300'  => __z('300x450'),
                    'w500'  => __z('500x750'),
                    'w780'  => __z('780x1170'),
                    'w1280'  => __z('1280x1920'),
                ),
                'default' => 'w500'
            ),			
			array(
                'id'    => 'backdrop_size',
                'type'  => 'radio',
                'title' => __z('Backdrop Size'),
                'options' => array(
                    'w92' => __z('92x52'),
                    'w154'  => __z('154x87'),
                    'w185'  => __z('185x104'),
                    'w300'  => __z('300x169'),
                    'w500'  => __z('500x281'),
                    'w780'  => __z('780x439'),
                    'w1280'  => __z('1280x720'),
                ),
                'default' => 'w500'
            ),
            array(
                'type' => 'subheading',
                'content' => __z('Seasons Poster')
            ),
            array(
                'id' => 'poster_style_ss',
                'type' => 'radio',
                'title' => __z('Poster Style'),
                'default' => 'horizontal',
                'options' => array(
                    'horizontal' => __z('Horizontal'),
                    'vertical'  => __z('Vertical')
                )
            ),
            array(
                'id' => 'poster_source_ss',
                'type' => 'radio',
                'title' => __z('Poster Source'),
                'default' => 'thumb',
                'options' => array(
                    'meta' => __z('Post Meta'),
                    'thumb'  => __z('Featured Image')
                )
            ),			
            array(
                'id' => 'poster_meta_source_ss',
                'type' => 'radio',
                'title' => __z('Meta Source'),
                'default' => 'zt_poster',
                'options' => array(
                    'zt_poster' => __z('Poster Image'),
                    'zt_backdrop'  => __z('Backdrop Image')
                ),
				'dependency' => array('poster_source_ss', '==', 'meta')
            ),
            array(
                'id' => 'poster_imagess',
                'type' => 'radio',
                'title' => __z('Poster Source'),
                'default' => 'inherit',
                'options' => array(
                    'inherit' => __z('Inherit from TV Show'),
                    'default'  => __z('Default post image')
                )
            ),
            array(
                'type' => 'subheading',
                'content' => __z('Episodes Poster')
            ),
            array(
                'id' => 'poster_style_ep',
                'type' => 'radio',
                'title' => __z('Poster Style'),
                'default' => 'horizontal',
                'options' => array(
                    'horizontal' => __z('Horizontal'),
                    'vertical'  => __z('Vertical')
                )
            ),
            array(
                'id' => 'poster_source_ep',
                'type' => 'radio',
                'title' => __z('Poster Source'),
                'default' => 'meta',
                'options' => array(
                    'meta' => __z('Post Meta'),
                    'thumb'  => __z('Featured Image')
                )
            ),
            array(
                'id' => 'poster_meta_source_ep',
                'type' => 'radio',
                'title' => __z('Meta Source'),
                'default' => 'zt_backdrop',
                'options' => array(
                    'zt_poster' => __z('Poster Image'),
                    'zt_backdrop'  => __z('Backdrop Image')
                ),
				'dependency' => array('poster_source_ep', '==', 'meta')
            ),
            array(
                'id' => 'poster_imageep',
                'type' => 'radio',
                'title' => __z('Poster Source'),
                'default' => 'default',
                'options' => array(
                    'inherittv' => __z('Inherit from TV Show'),
					'inheritss' => __z('Inherit from Season'),
                    'default'  => __z('Default post image')
                )
            ),

        )
    )
);
